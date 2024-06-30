package io.kneo.projects.controller;

import io.kneo.core.controller.AbstractSecuredController;
import io.kneo.core.dto.actions.ActionBox;
import io.kneo.core.dto.cnst.PayloadType;
import io.kneo.core.dto.form.FormPage;
import io.kneo.core.dto.view.View;
import io.kneo.core.dto.view.ViewPage;
import io.kneo.core.localization.LanguageCode;
import io.kneo.core.model.user.IUser;
import io.kneo.core.repository.exception.DocumentHasNotFoundException;
import io.kneo.core.repository.exception.DocumentModificationAccessException;
import io.kneo.core.service.UserService;
import io.kneo.core.util.RuntimeUtil;
import io.kneo.projects.dto.ProjectDTO;
import io.kneo.projects.dto.actions.ProjectActionsFactory;
import io.kneo.projects.model.Project;
import io.kneo.projects.model.cnst.ProjectStatusType;
import io.kneo.projects.service.ProjectService;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RouteBase;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@RolesAllowed("**")
@RouteBase(path = "/api/:org/projects")
public class ProjectController extends AbstractSecuredController<Project, ProjectDTO> {

    @Inject
    ProjectService service;

    public ProjectController(UserService userService) {
        super(userService);
    }

    @Route(path = "", methods = Route.HttpMethod.GET, produces = "application/json")
    public void get(RoutingContext rc) {
        int page = Integer.parseInt(rc.request().getParam("page", "1"));
        int size = Integer.parseInt(rc.request().getParam("size", "10"));

        Optional<IUser> userOptional = getUserId(rc);
        if (userOptional.isPresent()) {
            IUser user = userOptional.get();

            Uni.combine().all().unis(
                    service.getAllCount(user.getId()),
                    service.getAll(size, (page - 1) * size, user.getId())
            ).asTuple().subscribe().with(
                    tuple -> {
                        int count = tuple.getItem1();
                        List<ProjectDTO> projects = tuple.getItem2();

                        int maxPage = RuntimeUtil.countMaxPage(count, size);

                        ViewPage viewPage = new ViewPage();
                        View<ProjectDTO> dtoEntries = new View<>(projects, count, page, maxPage, size);
                        viewPage.addPayload(PayloadType.VIEW_DATA, dtoEntries);

                        ActionBox actions = ProjectActionsFactory.getViewActions(user.getActivatedRoles());
                        viewPage.addPayload(PayloadType.CONTEXT_ACTIONS, actions);

                        rc.response().setStatusCode(200).end(JsonObject.mapFrom(viewPage).encode());
                    },
                    failure -> {
                        LOGGER.error("Error processing request: ", failure);
                        rc.response().setStatusCode(500).end("Internal Server Error");
                    }
            );
        } else {
            rc.response().setStatusCode(403).end(String.format("%s is not allowed", getUserOIDCName(rc)));
        }
    }

    @Route(path = "/search/:keyword", methods = Route.HttpMethod.GET, produces = "application/json")
    public void search(RoutingContext rc) {
        String keyword = rc.pathParam("keyword");
        service.search(keyword).subscribe().with(
                projects -> {
                    ViewPage viewPage = new ViewPage();
                    viewPage.addPayload(PayloadType.VIEW_DATA, projects);
                    rc.response().setStatusCode(200).end(JsonObject.mapFrom(viewPage).encode());
                },
                failure -> {
                    LOGGER.error("Error processing request: ", failure);
                    rc.response().setStatusCode(500).end("Internal Server Error");
                }
        );
    }

    @Route(path = "/status/:status", methods = Route.HttpMethod.GET, produces = "application/json")
    public void searchByStatus(RoutingContext rc) {
        String statusParam = rc.pathParam("status");
        try {
            ProjectStatusType status = ProjectStatusType.valueOf(statusParam.toUpperCase());
            if (status == ProjectStatusType.UNKNOWN) {
                rc.response().setStatusCode(400).end("Invalid status value.");
                return;
            }

            service.searchByStatus(status).subscribe().with(
                    projects -> {
                        ViewPage viewPage = new ViewPage();
                        viewPage.addPayload(PayloadType.VIEW_DATA, projects);
                        rc.response().setStatusCode(200).end(JsonObject.mapFrom(viewPage).encode());
                    },
                    failure -> {
                        LOGGER.error("Error processing request: ", failure);
                        rc.response().setStatusCode(500).end("Internal Server Error");
                    }
            );
        } catch (IllegalArgumentException e) {
            rc.response().setStatusCode(400).end("Invalid status value.");
        }
    }

    @Route(path = "/:id", methods = Route.HttpMethod.GET, produces = "application/json")
    public void getById(RoutingContext rc) {
        String id = rc.pathParam("id");
        Optional<IUser> userOptional = getUserId(rc);
        if (userOptional.isPresent()) {
            IUser user = userOptional.get();
            LanguageCode languageCode = LanguageCode.valueOf(rc.request().getParam("lang", LanguageCode.ENG.name()));

            service.getDTO(id, user, languageCode).subscribe().with(
                    project -> {
                        FormPage page = new FormPage();
                        page.addPayload(PayloadType.DOC_DATA, project);
                        page.addPayload(PayloadType.CONTEXT_ACTIONS, new ActionBox());
                        rc.response().setStatusCode(200).end(JsonObject.mapFrom(page).encode());
                    },
                    failure -> {
                        if (failure instanceof DocumentHasNotFoundException) {
                            rc.response().setStatusCode(404).end("Project not found");
                        } else {
                            LOGGER.error("Error processing request: ", failure);
                            rc.response().setStatusCode(500).end("Internal Server Error");
                        }
                    }
            );
        } else {
            rc.response().setStatusCode(403).end(String.format("%s is not allowed", getUserOIDCName(rc)));
        }
    }

    @Route(path = "/", methods = Route.HttpMethod.POST, consumes = "application/json", produces = "application/json")
    public void create(RoutingContext rc) {
        try {
            JsonObject jsonObject = rc.body().asJsonObject();
            ProjectDTO dto = jsonObject.mapTo(ProjectDTO.class);
            Optional<IUser> userOptional = getUserId(rc);

            if (userOptional.isPresent()) {
                service.add(dto, userOptional.get()).subscribe().with(
                        createdProject -> rc.response().setStatusCode(201).end(JsonObject.mapFrom(createdProject).encode()),
                        failure -> {
                            LOGGER.error(failure.getMessage(), failure);
                            rc.response().setStatusCode(500).end(failure.getMessage());
                        }
                );
            } else {
                rc.response().setStatusCode(403).end(String.format("%s is not allowed", getUserOIDCName(rc)));
            }
        } catch (DecodeException e) {
            LOGGER.error("Error decoding request body: {}", e.getMessage());
            rc.response().setStatusCode(400).end("Invalid request body");
        }
    }

    @Route(path = "/:id", methods = Route.HttpMethod.PUT, consumes = "application/json", produces = "application/json")
    public void update(RoutingContext rc) {
        String id = rc.pathParam("id");
        try {
            JsonObject jsonObject = rc.body().asJsonObject();
            ProjectDTO dto = jsonObject.mapTo(ProjectDTO.class);
            Optional<IUser> userOptional = getUserId(rc);

            if (userOptional.isPresent()) {
                service.update(id, dto, userOptional.get()).subscribe().with(
                        updatedProject -> rc.response().setStatusCode(200).end(JsonObject.mapFrom(updatedProject).encode()),
                        failure -> {
                            if (failure instanceof DocumentModificationAccessException) {
                                rc.response().setStatusCode(403).end("Access denied for document modification");
                            } else {
                                LOGGER.error(failure.getMessage(), failure);
                                rc.response().setStatusCode(500).end("Internal Server Error");
                            }
                        }
                );
            } else {
                rc.response().setStatusCode(403).end(String.format("%s is not allowed", getUserOIDCName(rc)));
            }
        } catch (DecodeException e) {
            LOGGER.error("Error decoding request body: {}", e.getMessage());
            rc.response().setStatusCode(400).end("Invalid request body");
        }
    }

    @Route(path = "/:id", methods = Route.HttpMethod.DELETE, produces = "application/json")
    public void delete(RoutingContext rc) throws DocumentModificationAccessException {
        String id = rc.pathParam("id");
        Optional<IUser> userOptional = getUserId(rc);
        if (userOptional.isPresent()) {
            service.delete(id, userOptional.get()).subscribe().with(
                    count -> rc.response().setStatusCode(count > 0 ? 204 : 404).end(),
                    failure -> {
                        LOGGER.error(failure.getMessage(), failure);
                        rc.response().setStatusCode(500).end("Internal Server Error");
                    }
            );
        } else {
            rc.response().setStatusCode(403).end(String.format("%s is not allowed", getUserOIDCName(rc)));
        }
    }
}