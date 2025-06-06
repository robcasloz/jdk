/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.source.tree;

/**
 * A tree node for an import declaration.
 *
 * For example:
 * <pre>
 *   import <em>qualifiedIdentifier</em> ;
 *
 *   import static <em>qualifiedIdentifier</em> ;
 * </pre>
 *
 * @jls 7.5 Import Declarations
 *
 * @author Peter von der Ah&eacute;
 * @author Jonathan Gibbons
 * @since 1.6
 */
public interface ImportTree extends Tree {
    /**
     * Returns true if this is a static import declaration.
     * @return true if this is a static import
     */
    boolean isStatic();

    /**
     * {@return true if this is an module import declaration.}
     * @since 25
     */
    boolean isModule();

    /**
     * Returns the qualified identifier for the declaration(s)
     * being imported.
     * If this is an import-on-demand declaration, the
     * qualified identifier will end in "*".
     * @return a qualified identifier, ending in "*" if and only if
     * this is an import-on-demand
     */
    Tree getQualifiedIdentifier();
}
