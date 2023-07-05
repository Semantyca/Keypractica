package com.semantyca.controller;

import com.semantyca.dto.document.UserDTO;
import com.semantyca.dto.view.View;
import com.semantyca.dto.view.ViewOptionsFactory;
import com.semantyca.dto.view.ViewPage;
import com.semantyca.model.user.User;
import com.semantyca.repository.exception.DocumentModificationAccessException;
import com.semantyca.service.UserService;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserController {

    @Inject
    UserService service;

    @GET
    @Path("/")
    public Response get()  {
        ViewPage viewPage = new ViewPage();
        viewPage.addPayload("view_options", ViewOptionsFactory.getProjectOptions());
        View<User> view = new View<>(service.getAll().await().indefinitely());
        viewPage.addPayload("view_data", view);
        return Response.ok(viewPage).build();
    }

    @GET
    @Path("/stream")
    @Consumes(MediaType.SERVER_SENT_EVENTS)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<User> getStream() {
        return service.getAllStream();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") String id)  {
        User user = service.get(id);
        return Response.ok(user).build();
    }

    @POST
    @Path("/")
    public Response create(UserDTO userDTO) {
        return Response.created(URI.create("/" + service.add(userDTO))).build();
    }

    @PUT
    @Path("/")
    public Response update(UserDTO userDTO) throws DocumentModificationAccessException {
        return Response.ok(URI.create("/" + service.update(userDTO).getIdentifier())).build();
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteWord(@PathParam("id") String id) {
        return Response.ok().build();
    }

}
