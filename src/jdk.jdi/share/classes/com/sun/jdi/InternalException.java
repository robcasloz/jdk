/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jdi;

/**
 * Thrown to indicate that an unexpected internal error has
 * occurred.
 *
 * @author Gordon Hirsch
 * @since  1.3
 */
public class InternalException extends RuntimeException {

    private static final long serialVersionUID = -9171606393104480607L;

    /** @serial */
    private int errorCode;

    public InternalException() {
        super();
        this.errorCode = 0;
    }

    public InternalException(String s) {
        super(s);
        this.errorCode = 0;
    }

    public InternalException(int errorCode) {
        super();
        this.errorCode = errorCode;
    }

    public InternalException(String s, int errorCode) {
        super(s);
        this.errorCode = errorCode;
    }

    public int errorCode() {
        return errorCode;
    }
}
