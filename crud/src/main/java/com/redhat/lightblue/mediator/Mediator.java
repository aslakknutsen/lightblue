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
package com.redhat.lightblue.mediator;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.redhat.lightblue.DeleteRequest;
import com.redhat.lightblue.FindRequest;
import com.redhat.lightblue.InsertionRequest;
import com.redhat.lightblue.OperationStatus;
import com.redhat.lightblue.Response;
import com.redhat.lightblue.SaveRequest;
import com.redhat.lightblue.UpdateRequest;
import com.redhat.lightblue.crud.Operation;
import com.redhat.lightblue.crud.CRUDController;
import com.redhat.lightblue.crud.CRUDDeleteResponse;
import com.redhat.lightblue.crud.CRUDFindResponse;
import com.redhat.lightblue.crud.CRUDUpdateResponse;
import com.redhat.lightblue.crud.ConstraintValidator;
import com.redhat.lightblue.crud.DocCtx;
import com.redhat.lightblue.crud.CrudConstants;
import com.redhat.lightblue.crud.Factory;
import com.redhat.lightblue.metadata.EntityMetadata;
import com.redhat.lightblue.metadata.Metadata;
import com.redhat.lightblue.metadata.PredefinedFields;
import com.redhat.lightblue.util.Error;
import com.redhat.lightblue.util.JsonDoc;
import com.redhat.lightblue.util.Path;

/**
 * The mediator looks at a request, performs basic validation, and passes the operation to one or more of the
 * controllers based on the request attributes.
 */
public class Mediator {

    public static final String CRUD_MSG_PREFIX = "CRUD controller={}";

    private static final Logger LOGGER = LoggerFactory.getLogger(Mediator.class);

    private static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.withExactBigDecimals(true);

    private static final Path OBJECT_TYPE_PATH = new Path("object_type");

    private final Metadata metadata;
    private final Factory factory;

    public Mediator(Metadata md,
                    Factory factory) {
        this.metadata = md;
        this.factory = factory;
    }

    /**
     * Inserts data
     *
     * @param req Insertion request
     *
     * Mediator performs constraint and role validation, and passes documents that pass the validation to the CRUD
     * implementation for that entity. CRUD implementation can perform further validations.
     */
    public Response insert(InsertionRequest req) {
        LOGGER.debug("insert {}", req.getEntityVersion());
        Error.push("insert(" + req.getEntityVersion().toString() + ")");
        Response response = new Response();
        try {
            OperationContext ctx = OperationContext.getInstance(req, metadata, factory, NODE_FACTORY, Operation.INSERT);
            EntityMetadata md = ctx.getTopLevelEntityMetadata();
            if (!md.getAccess().getInsert().hasAccess(ctx.getCallerRoles())) {
                ctx.setStatus(OperationStatus.ERROR);
                ctx.addError(Error.get(CrudConstants.ERR_NO_ACCESS, "insert " + ctx.getTopLevelEntityName()));
            } else {
                updatePredefinedFields(ctx.getDocuments(), md.getName());
                runBulkConstraintValidation(ctx);
                if (!ctx.hasErrors() && ctx.hasDocumentsWithoutErrors()) {
                    CRUDController controller = factory.getCRUDController(md);
                    LOGGER.debug(CRUD_MSG_PREFIX, controller.getClass().getName());
                    controller.insert(ctx, req.getReturnFields());
                    ctx.getHookManager().queueMediatorHooks(ctx);
                    List<JsonDoc> insertedDocuments = ctx.getOutputDocumentsWithoutErrors();
                    if (!insertedDocuments.isEmpty()) {
                        response.setEntityData(JsonDoc.listToDoc(insertedDocuments, NODE_FACTORY));
                    }
                    response.setModifiedCount(insertedDocuments.size());
                    if (insertedDocuments.size() == ctx.getDocuments().size()) {
                        ctx.setStatus(OperationStatus.COMPLETE);
                    } else if (!insertedDocuments.isEmpty()) {
                        ctx.setStatus(OperationStatus.PARTIAL);
                    } else {
                        ctx.setStatus(OperationStatus.ERROR);
                    }
                } else {
                    ctx.setStatus(OperationStatus.ERROR);
                }
            }
            response.getDataErrors().addAll(ctx.getDataErrors());
            response.getErrors().addAll(ctx.getErrors());
            response.setStatus(ctx.getStatus());
            if(response.getStatus()!=OperationStatus.ERROR)
                ctx.getHookManager().callQueuedHooks();
        } catch (Error e) {
            response.getErrors().add(e);
            response.setStatus(OperationStatus.ERROR);
        } catch (Exception e) {
            response.getErrors().add(Error.get(CrudConstants.ERR_CRUD, e.toString()));
            response.setStatus(OperationStatus.ERROR);
        } finally {
            Error.pop();
        }
        return response;
    }

    /**
     * Saves data. Documents in the DB that match the ID of the documents in the request are rewritten. If a document
     * does not exist in the DB and upsert=true, the document is inserted.
     *
     * @param req Save request
     *
     * Mediator performs constraint validation, and passes documents that pass the validation to the CRUD implementation
     * for that entity. CRUD implementation can perform further validations.
     *
     */
    public Response save(SaveRequest req) {
        LOGGER.debug("save {}", req.getEntityVersion());
        Error.push("save(" + req.getEntityVersion().toString() + ")");
        Response response = new Response();
        try {
            OperationContext ctx = OperationContext.getInstance(req, metadata, factory, NODE_FACTORY, Operation.SAVE);
            EntityMetadata md = ctx.getTopLevelEntityMetadata();
            if (!md.getAccess().getUpdate().hasAccess(ctx.getCallerRoles())
                    || (req.isUpsert() && !md.getAccess().getInsert().hasAccess(ctx.getCallerRoles()))) {
                ctx.setStatus(OperationStatus.ERROR);
                ctx.addError(Error.get(CrudConstants.ERR_NO_ACCESS, "insert/update " + ctx.getTopLevelEntityName()));
            } else {
                updatePredefinedFields(ctx.getDocuments(), md.getName());
                runBulkConstraintValidation(ctx);
                if (!ctx.hasErrors() && ctx.hasDocumentsWithoutErrors()) {
                    CRUDController controller = factory.getCRUDController(md);
                    LOGGER.debug(CRUD_MSG_PREFIX, controller.getClass().getName());
                    controller.save(ctx, req.isUpsert(), req.getReturnFields());
                    ctx.getHookManager().queueMediatorHooks(ctx);
                    List<JsonDoc> updatedDocuments = ctx.getOutputDocumentsWithoutErrors();
                    if (!updatedDocuments.isEmpty()) {
                        response.setEntityData(JsonDoc.listToDoc(updatedDocuments, NODE_FACTORY));
                    }
                    response.setModifiedCount(updatedDocuments.size());
                    if (updatedDocuments.size() == ctx.getDocuments().size()) {
                        ctx.setStatus(OperationStatus.COMPLETE);
                    } else if (!updatedDocuments.isEmpty()) {
                        ctx.setStatus(OperationStatus.PARTIAL);
                    } else {
                        ctx.setStatus(OperationStatus.ERROR);
                    }
                }
            }
            response.getDataErrors().addAll(ctx.getDataErrors());
            response.getErrors().addAll(ctx.getErrors());
            response.setStatus(ctx.getStatus());
            if(response.getStatus()!=OperationStatus.ERROR)
                ctx.getHookManager().callQueuedHooks();
        } catch (Error e) {
            response.getErrors().add(e);
            response.setStatus(OperationStatus.ERROR);
        } catch (Exception e) {
            response.getErrors().add(Error.get(CrudConstants.ERR_CRUD, e.toString()));
            response.setStatus(OperationStatus.ERROR);
        } finally {
            Error.pop();
        }
        return response;
    }

    /**
     * Updates documents that match the given search criteria
     *
     * @param req Update request
     *
     * All documents matching the search criteria are updated using the update expression given in the request. Then,
     * the updated document is projected and returned in the response.
     *
     * The mediator does not perform any constraint validation. The CRUD implementation must perform all constraint
     * validations and process only the documents that pass those validations.
     */
    public Response update(UpdateRequest req) {
        LOGGER.debug("update {}", req.getEntityVersion());
        Error.push("update(" + req.getEntityVersion().toString() + ")");
        Response response = new Response();
        try {
            OperationContext ctx = OperationContext.getInstance(req, metadata, factory, NODE_FACTORY, Operation.UPDATE);
            EntityMetadata md = ctx.getTopLevelEntityMetadata();
            if (!md.getAccess().getUpdate().hasAccess(ctx.getCallerRoles())) {
                ctx.setStatus(OperationStatus.ERROR);
                ctx.addError(Error.get(CrudConstants.ERR_NO_ACCESS, "update " + ctx.getTopLevelEntityName()));
            } else {
                CRUDController controller = factory.getCRUDController(md);
                LOGGER.debug(CRUD_MSG_PREFIX, controller.getClass().getName());
                CRUDUpdateResponse updateResponse = controller.update(ctx,
                        req.getQuery(),
                        req.getUpdateExpression(),
                        req.getReturnFields());
                ctx.getHookManager().queueMediatorHooks(ctx);
                LOGGER.debug("# Updated", updateResponse.getNumUpdated());
                response.setModifiedCount(updateResponse.getNumUpdated());
                if (ctx.hasErrors()) {
                    ctx.setStatus(OperationStatus.ERROR);
                } else {
                    ctx.setStatus(OperationStatus.COMPLETE);
                }
            }
            response.getErrors().addAll(ctx.getErrors());
            response.setStatus(ctx.getStatus());
            if(response.getStatus()!=OperationStatus.ERROR)
                ctx.getHookManager().callQueuedHooks();
       } catch (Error e) {
            response.getErrors().add(e);
            response.setStatus(OperationStatus.ERROR);
        } catch (Exception e) {
            response.getErrors().add(Error.get(CrudConstants.ERR_CRUD, e.toString()));
            response.setStatus(OperationStatus.ERROR);
        } finally {
            Error.pop();
        }
        return response;
    }

    public Response delete(DeleteRequest req) {
        LOGGER.debug("delete {}", req.getEntityVersion());
        Error.push("delete(" + req.getEntityVersion().toString() + ")");
        Response response = new Response();
        try {
            OperationContext ctx = OperationContext.getInstance(req, metadata, factory, NODE_FACTORY, Operation.DELETE);
            EntityMetadata md = ctx.getTopLevelEntityMetadata();
            if (!md.getAccess().getDelete().hasAccess(ctx.getCallerRoles())) {
                ctx.setStatus(OperationStatus.ERROR);
                ctx.addError(Error.get(CrudConstants.ERR_NO_ACCESS, "delete " + ctx.getTopLevelEntityName()));
            } else {
                CRUDController controller = factory.getCRUDController(md);
                LOGGER.debug(CRUD_MSG_PREFIX, controller.getClass().getName());
                CRUDDeleteResponse result = controller.delete(ctx,
                        req.getQuery());
                ctx.getHookManager().queueMediatorHooks(ctx);
                response.setModifiedCount(result.getNumDeleted());
                if (ctx.hasErrors()) {
                    ctx.setStatus(OperationStatus.ERROR);
                } else {
                    ctx.setStatus(OperationStatus.COMPLETE);
                }
            }
            response.getErrors().addAll(ctx.getErrors());
            response.setStatus(ctx.getStatus());
            if(response.getStatus()!=OperationStatus.ERROR)
                ctx.getHookManager().callQueuedHooks();
        } catch (Error e) {
            response.getErrors().add(e);
            response.setStatus(OperationStatus.ERROR);
        } catch (Exception e) {
            response.getErrors().add(Error.get(CrudConstants.ERR_CRUD, e.toString()));
            response.setStatus(OperationStatus.ERROR);
        } finally {
            Error.pop();
        }
        return response;
    }

    /**
     * Finds documents
     *
     * @param req Find request
     *
     * The implementation passes the request to the back-end.
     */
    public Response find(FindRequest req) {
        LOGGER.debug("find {}", req.getEntityVersion());
        Error.push("find(" + req.getEntityVersion().toString() + ")");
        Response response = new Response();
        response.setStatus(OperationStatus.ERROR);
        try {
            OperationContext ctx = OperationContext.getInstance(req, metadata, factory, NODE_FACTORY, Operation.FIND);
            EntityMetadata md = ctx.getTopLevelEntityMetadata();
            if (!md.getAccess().getFind().hasAccess(ctx.getCallerRoles())) {
                ctx.setStatus(OperationStatus.ERROR);
                LOGGER.debug("No access");
                ctx.addError(Error.get(CrudConstants.ERR_NO_ACCESS, "find " + ctx.getTopLevelEntityName()));
            } else {
                CRUDController controller = factory.getCRUDController(md);
                LOGGER.debug(CRUD_MSG_PREFIX, controller.getClass().getName());
                CRUDFindResponse result = controller.find(ctx,
                        req.getQuery(),
                        req.getProjection(),
                        req.getSort(),
                        req.getFrom(),
                        req.getTo());
                ctx.getHookManager().queueMediatorHooks(ctx);
                ctx.setStatus(OperationStatus.COMPLETE);
                response.setMatchCount(result.getSize());
                List<DocCtx> documents = ctx.getDocuments();
                if (documents != null) {
                    List<JsonDoc> resultList = new ArrayList<>(documents.size());
                    for (DocCtx doc : documents) {
                        resultList.add(doc.getOutputDocument());
                    }
                    response.setEntityData(JsonDoc.listToDoc(resultList, NODE_FACTORY));
                }
            }
            response.setStatus(ctx.getStatus());
            response.getErrors().addAll(ctx.getErrors());
            if(response.getStatus()!=OperationStatus.ERROR)
                ctx.getHookManager().callQueuedHooks();
        } catch (Error e) {
            LOGGER.debug("Error during find:{}", e);
            response.getErrors().add(e);
        } catch (Exception e) {
            LOGGER.debug("Exception during find:{}", e);
            response.getErrors().add(Error.get(CrudConstants.ERR_CRUD, e.toString()));
        } finally {
            Error.pop();
        }
        return response;
    }

    /**
     * Runs constraint violation
     */
    private void runBulkConstraintValidation(OperationContext ctx) {
        LOGGER.debug("Bulk constraint validation");
        EntityMetadata md = ctx.getTopLevelEntityMetadata();
        ConstraintValidator constraintValidator = factory.getConstraintValidator(md);
        List<DocCtx> docs = ctx.getDocumentsWithoutErrors();
        constraintValidator.validateDocs(docs);
        Map<JsonDoc, List<Error>> docErrors = constraintValidator.getDocErrors();
        for (Map.Entry<JsonDoc, List<Error>> entry : docErrors.entrySet()) {
            JsonDoc doc = entry.getKey();
            List<Error> errors = entry.getValue();
            if (errors != null && !errors.isEmpty()) {
                ((DocCtx) doc).addErrors(errors);
            }
        }
        List<Error> errors = constraintValidator.getErrors();
        if (errors != null && !errors.isEmpty()) {
            ctx.addErrors(errors);
        }
        LOGGER.debug("Constraint validation complete");
    }

    private void updatePredefinedFields(List<DocCtx> docs, String entity) {
        for (JsonDoc doc : docs) {
            PredefinedFields.updateArraySizes(NODE_FACTORY, doc);
            JsonNode node = doc.get(OBJECT_TYPE_PATH);
            if (node == null) {
                doc.modify(OBJECT_TYPE_PATH, NODE_FACTORY.textNode(entity), false);
            } else if (!node.asText().equals(entity)) {
                throw Error.get(CrudConstants.ERR_INVALID_ENTITY, node.asText());
            }
        }
    }
}
