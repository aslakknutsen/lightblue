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
package com.redhat.lightblue.metadata.constraints;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.redhat.lightblue.metadata.EntityConstraint;

public class ReferencesConstraint implements EntityConstraint, Serializable {

    private static final long serialVersionUID = 1l;

    public static final String REFERENCES = "references";

    private final ArrayList<Reference> references = new ArrayList<Reference>();

    public String getType() {
        return REFERENCES;
    }

    @SuppressWarnings("unchecked")
    public List<Reference> getReferences() {
        return (ArrayList<Reference>) references.clone();
    }

    public void setReferences(List<Reference> f) {
        references.clear();
        if (f != null) {
            references.addAll(f);
        }
    }
}
