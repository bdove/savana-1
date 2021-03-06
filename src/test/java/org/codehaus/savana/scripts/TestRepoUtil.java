package org.codehaus.savana.scripts;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.mutable.MutableInt;
import org.codehaus.savana.BranchType;
import org.codehaus.savana.WCUtil;
import org.codehaus.savana.scripts.admin.CreateMetadataFile;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public abstract class TestRepoUtil {
    private static final Logger _sLog = Logger.getLogger(TestRepoUtil.class.getName());

    static {
        // Internally SVNKit sleeps for about a second after most update operations
        // to make sure timestamps are distinct--the 'svn status' operation relies
        // on timestamps to detect changes where the file size stays the same, and
        // it uses per-second resolution.  As long as the test cases don't generate
        // changes where file sizes stay the same, we should be able to disable the
        // sleeps and run tests at full speed, about a 10x improvement.
        //
        // Note: subversion 1.4 repos appear not to store timestamps--see the source for
        // org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14#INAPPLICABLE_PROPERTIES
        // which includes SVNProperty.WORKING_SIZE.  So sleeping is only disabled w/1.5+.
        if (!TestSvnUtil.REPO_PRE15) {
            SVNFileUtil.setSleepForTimestamp(false);
        }
    }

    public static File SUBVERSION_CONFIG_DIR = TestDirUtil.createTempDir("subversion-config");

    public static final SVNClientManager SVN = createClientManager();

    /**
     * Create a repository that most test cases can share.
     */
    public static final SVNURL DEFAULT_REPO = newRepositoryNoCheckedExceptions(true);

    /**
     * Create a SVNClientManager that test cases can use to interact with SVNKit directly.
     */
    private static SVNClientManager createClientManager() {
        DefaultSVNOptions options = new DefaultSVNOptions(SUBVERSION_CONFIG_DIR, true);
        options.setInteractiveConflictResolution(false);

        ISVNAuthenticationManager authManager =
                SVNWCUtil.createDefaultAuthenticationManager(SUBVERSION_CONFIG_DIR, "savana-user", "", true);

        return SVNClientManager.newInstance(options, authManager);
    }

    /**
     * Create a new test subversion repository.
     */
    public static SVNURL newRepository(boolean installHooks) throws SVNException, IOException {
        _sLog.info("creating test repository");

        FSRepositoryFactory.setup();

        // configure SVNKit to use file formats that match the installed version of the subversion client
        WCUtil.setSupportedWorkingCopyFormatVersion(TestSvnUtil.WC_FORMAT);

        // create the repository using file formats that match the installed version of the subversion server
        File repoDir = TestDirUtil.createTempDir(nextRepositoryName());
        SVNAdminClient adminClient = SVN.getAdminClient();
        SVNURL repoUrl = adminClient.doCreateRepository(repoDir, null, false, true,
                TestSvnUtil.REPO_PRE14, TestSvnUtil.REPO_PRE15, TestSvnUtil.REPO_PRE16);

        // install savana preferred subversion hooks into the test repository
        if (installHooks) {
            for (File svnHookFile : TestDirUtil.SVN_HOOKS_DIR.listFiles()) {
                if (svnHookFile.isFile() && !svnHookFile.isHidden() &&
                        !svnHookFile.getName().endsWith(".properties")) {
                    File repoHookFile = new File(new File(repoDir, "hooks"), svnHookFile.getName());
                    FileUtils.copyFile(svnHookFile, repoHookFile, false);
                    SVNFileUtil.setExecutable(repoHookFile, true);
                }
            }
        }

        return repoUrl;
    }

    private static SVNURL newRepositoryNoCheckedExceptions(boolean installHooks) {
        try {
            return newRepository(installHooks);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Creates a project in the repo and an associated working directory, for a project that uses the standard branch paths. */
    public static File setupProjectWithWC(SVNURL repoUrl, String projectRoot,
                                          boolean configureSavana, boolean installPolicies,
                                          String resourceToImport)
            throws Exception {
        _sLog.info("creating new project " + projectRoot);

        SVNURL projectUrl = repoUrl.appendPath(projectRoot, false);

        // delete any existing project at the new project URL
        SVNRepository repository = SVN.createRepository(repoUrl, true);
        repository.setLocation(repoUrl, false);
        if (repository.checkPath(projectRoot, -1) != SVNNodeKind.NONE) {
            SVN.getCommitClient().doDelete(new SVNURL[]{projectUrl}, "branch admin - delete old project", null);
        }

        // setup initial branching structure
        SVN.getCommitClient().doMkDir(new SVNURL[] {
                projectUrl.appendPath(BranchType.TRUNK.getDefaultPath(), false),
                projectUrl.appendPath(BranchType.RELEASE_BRANCH.getDefaultPath(), false),
                projectUrl.appendPath(BranchType.USER_BRANCH.getDefaultPath(), false),
        }, "branch admin - setup initial branch directories", null, true);

        // import initial project files from a directory in the classpath
        if (resourceToImport != null) {
            File importDir = new File(TestRepoUtil.class.getClassLoader().getResource(resourceToImport).toURI());
            SVNURL trunkUrl = projectUrl.appendPath(BranchType.TRUNK.getDefaultPath(), false);
            SVN.getCommitClient().doImport(importDir, trunkUrl, "trunk - initial import", null, true, false, SVNDepth.INFINITY);
        }

        // check out the project, start in the trunk
        File wc = createTrunkWC(repoUrl, projectRoot);

        // create the .savana metadata file
        if (configureSavana) {
            if (installPolicies) {
                TestSavanaUtil.savana(CreateMetadataFile.class, projectRoot, "TRUNK",
                        "--savanaPoliciesFile", TestDirUtil.POLICIES_FILE.getAbsolutePath());
            } else {
                TestSavanaUtil.savana(CreateMetadataFile.class, projectRoot, "TRUNK");

            }
            SVN.getCommitClient().doCommit(new File[] {wc}, false,
                    "trunk - initial setup of savana", null, null, false, false, SVNDepth.INFINITY);
        }

        // get the workspace up-to-date
        SVN.getUpdateClient().doUpdate(wc, SVNRevision.HEAD, SVNDepth.INFINITY, false, false);

        return wc;
    }

    /** Create a directory containing the trunk of the specified project, for a project that uses the standard branch paths. */
    public static File createTrunkWC(SVNURL repoUrl, String projectRoot) throws SVNException {
        SVNURL projectUrl = repoUrl.appendPath(projectRoot, false);

        // check out the project, start in the trunk
        File wc = TestDirUtil.createTempDir(nextWorkingDirName(projectRoot));
        SVNURL trunkUrl = projectUrl.appendPath(BranchType.TRUNK.getDefaultPath(), false);
        SVN.getUpdateClient().doCheckout(trunkUrl, wc, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNDepth.INFINITY, false);

        // cd into the wc dir
        TestDirUtil.cd(wc);

        return wc;
    }

    private static int _sRepositoryCounter;
    private static final Map<String, MutableInt> _sWorkingDirCounters = new HashMap<String, MutableInt>();

    private static synchronized String nextRepositoryName() {
        return "savana-test-repo-" + toAlphaString(_sRepositoryCounter++);
    }

    private static synchronized String nextWorkingDirName(String projectRoot) {
        String projectName = projectRoot.replace('/', '-');
        MutableInt counter = _sWorkingDirCounters.get(projectName);
        if (counter == null) {
            _sWorkingDirCounters.put(projectName, counter = new MutableInt());
        }
        counter.increment();
        return "savana-test-wc-" + projectName + "-" + counter.intValue();
    }

    /** Returns a String from the sequence 'A', 'B', ..., 'Z', 'AA', ... 'ZZ', 'BA' ... */
    private static String toAlphaString(int value) {
        StringBuilder buf = new StringBuilder();
        do {
            buf.append((char)('A' + (value % 26)));
            value /= 26;
        } while (value != 0);
        buf.reverse();
        return buf.toString();
    }


    public static File touchCounterFile(File dir) throws IOException {
        File counterFile = new File(dir, "counter.txt");
        int value = Integer.parseInt(FileUtils.readFileToString(counterFile).trim());
        FileUtils.writeStringToFile(counterFile, Integer.toString(value + 1));
        return counterFile;
    }
}
