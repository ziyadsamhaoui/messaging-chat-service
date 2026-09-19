package com.ziyadsamhaoui.messagingchatservice.controller;

import com.ziyadsamhaoui.messagingchatservice.dto.request.CreateRoomRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.request.UpdateParticipantRequest;
import com.ziyadsamhaoui.messagingchatservice.dto.response.CursorPage;
import com.ziyadsamhaoui.messagingchatservice.dto.response.ParticipantResponse;
import com.ziyadsamhaoui.messagingchatservice.dto.response.RoomResponse;
import com.ziyadsamhaoui.messagingchatservice.security.CurrentUserProvider;
import com.ziyadsamhaoui.messagingchatservice.service.RoomService;
import com.ziyadsamhaoui.messagingchatservice.service.RoomService.CreationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService roomService;
    private final CurrentUserProvider currentUserProvider;

    public RoomController(RoomService roomService, CurrentUserProvider currentUserProvider) {
        this.roomService = roomService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        CreationResult result = roomService.createRoom(currentUserProvider.requireUserId(), request);
        RoomResponse body = RoomResponse.from(result.room(), result.callerRole());

        return result.newlyCreated() ? ResponseEntity.status(HttpStatus.CREATED).body(body) : ResponseEntity.ok(body);
    }

    @GetMapping
    public CursorPage<RoomResponse> listRooms(@RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {

        return roomService.listRooms(currentUserProvider.requireUserId(), cursor, limit);
    }

    @GetMapping("/{roomId}")
    public RoomResponse getRoom(@PathVariable String roomId) {
        return roomService.getRoom(currentUserProvider.requireUserId(), roomId);
    }

    @GetMapping("/{roomId}/participants")
    public CursorPage<ParticipantResponse> listParticipants(@PathVariable String roomId,
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {

        return roomService.listParticipants(currentUserProvider.requireUserId(), roomId, cursor, limit);
    }

    @PatchMapping("/{roomId}/participants/{userId}")
    public ParticipantResponse updateParticipant(@PathVariable String roomId, @PathVariable String userId,
            @Valid @RequestBody UpdateParticipantRequest request) {

        return roomService.updateParticipant(currentUserProvider.requireUserId(), roomId, userId, request);
    }

    @DeleteMapping("/{roomId}/participants/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeParticipant(@PathVariable String roomId, @PathVariable String userId) {
        roomService.removeParticipant(currentUserProvider.requireUserId(), roomId, userId);
    }
}
