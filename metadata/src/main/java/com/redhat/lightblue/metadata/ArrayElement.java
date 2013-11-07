/*
    Copyright 2013 Red Hat, Inc. and/or its affiliates.

    This file is part of lightblue.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.redhat.lightblue.metadata;

import java.io.Serializable;

import com.redhat.lightblue.util.TreeNode;
import com.redhat.lightblue.util.Path;

public abstract class ArrayElement implements TreeNode, Serializable {

    private static final long serialVersionUID = 1l;

    private String type;

    public ArrayElement() {}

    public ArrayElement(String type) {
        this.type=type;
    }

    /**
     * Gets the value of type
     * 
     * @return the value of type
     */
    public String getType() {
        return this.type;
    }

    /**
     * Sets the value of type
     * 
     * @param argType
     *            Value to assign to this.type
     */
    public void setType(String argType) {
        this.type = argType;
    }

    protected abstract TreeNode resolve(Path p,int level);
}