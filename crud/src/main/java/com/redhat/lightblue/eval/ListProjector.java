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
package com.redhat.lightblue.eval;

import java.util.List;
import java.util.ArrayList;

import com.redhat.lightblue.util.Path;

import com.redhat.lightblue.metadata.FieldTreeNode;

import com.redhat.lightblue.query.ProjectionList;
import com.redhat.lightblue.query.Projection;

public class ListProjector extends Projector {

    private final List<Projector> items;

    private Projector nestedProjector;

    public ListProjector(ProjectionList l, Path ctxPath, FieldTreeNode ctx) {
        super(ctxPath, ctx);
        List<Projection> projections = l.getItems();
        items = new ArrayList<Projector>(projections.size());
        for (Projection x : projections) {
            items.add(Projector.getInstance(x, ctxPath, ctx));
        }
    }

    @Override
    public Projector getNestedProjector() {
        return nestedProjector;
    }

    @Override
    public Boolean project(Path p, QueryEvaluationContext ctx) {
        nestedProjector = null;
        for (int n = items.size() - 1; n >= 0; n--) {
            Boolean projectionResult = items.get(n).project(p, ctx);
            if (projectionResult != null) {
                nestedProjector = items.get(n).getNestedProjector();
                return projectionResult;
            }
        }
        return null;
    }
}
