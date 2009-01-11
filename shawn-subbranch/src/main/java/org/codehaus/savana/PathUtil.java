/*
 * Savana - Transactional Workspaces for Subversion
 * Copyright (C) 2006-2009  Bazaarvoice Inc.
 * <p/>
 * This file is part of Savana.
 * <p/>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * Third party components of this software are provided or made available only subject
 * to their respective licenses. The relevant components and corresponding
 * licenses are listed in the "licenses" directory in this distribution. In any event,
 * the disclaimer of warranty and limitation of liability provision in this Agreement
 * will apply to all Software in this distribution.
 *
 * @author Brian Showers (brian@bazaarvoice.com)
 * @author Bryon Jacob (bryon@jacob.net)
 */
package org.codehaus.savana;

import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.util.LinkedList;
import java.util.List;

public class PathUtil {
    public static List<String> getAllSubpaths(String path) {
        LinkedList<String> subPaths = new LinkedList<String>();

        //Find all subpaths in reverse order
        while (path != null && path.length() > 0) {
            subPaths.addFirst(path);
            path = SVNPathUtil.removeTail(path);
        }

        return subPaths;
    }

    public static String getPathTail(String path, String prefixToRemove) {
        if (!path.startsWith(prefixToRemove)) {
            throw new IllegalArgumentException("ERROR: The path does not start with the expected prefix: " + path + " : " + prefixToRemove);
        }

        String tail = path.substring(prefixToRemove.length());
        if (tail.startsWith("/") || tail.startsWith("\\")) {
            tail = tail.substring(1);
        }

        return tail;
    }
}