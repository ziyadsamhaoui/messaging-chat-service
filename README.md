# BadrLink - Chat Service

**The conversation and messaging persistence service powering BadrLink, built with Java, Spring Boot, MongoDB, and Spring Security.**

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=flat-square\&logo=springboot\&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square\&logo=openjdk\&logoColor=white)](https://www.oracle.com/java/)
[![MongoDB](https://img.shields.io/badge/MongoDB-8-47A248?style=flat-square\&logo=mongodb\&logoColor=white)](https://www.mongodb.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square\&logo=docker\&logoColor=white)](https://www.docker.com/)
[![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=flat-square\&logo=apachemaven\&logoColor=white)](https://maven.apache.org/)

A dedicated microservice responsible for chat rooms, participants, messages, reactions, read cursors, and group invitations within BadrLink.

---

## Responsibilities

This service owns:

* Direct and group chat rooms
* Room participants and roles
* Messages and message history
* Message reactions
* Read cursors
* Group invitations
* Message editing and soft deletion
* Block enforcement for direct conversations

The service uses **MongoDB** for chat persistence and does not access databases owned by other services.

---

## Architecture

The Chat Service is part of the BadrLink microservices architecture.

```text
                    ┌─────────────────────┐
                    │   API Gateway       │
                    └──────────┬──────────┘
                               │
                          Bearer JWT
                               │
                               ▼
                    ┌─────────────────────┐
                    │    Chat Service     │
                    │      :8083          │
                    ├─────────────────────┤
                    │ Rooms               │
                    │ Participants        │
                    │ Messages            │
                    │ Reactions           │
                    │ Read Cursors        │
                    │ Invitations         │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │      MongoDB        │
                    │   messaging_chat    │
                    │      replica set    │
                    └─────────────────────┘
                               ▲
                               │
                    ┌──────────┴──────────┐
                    │                     │
                    │  Realtime Gateway   │
                    │                     │
                    └─────────────────────┘
```

The service validates Auth-issued JWTs using the Auth Service JWKS and communicates with the User Service for user and block information.

The service is **REST-only** in the current sprint. Real-time WebSocket/STOMP delivery is handled by the Realtime Gateway in a later sprint.

---

## API

### Rooms

| Method   | Endpoint                                | Description                   |
| -------- | --------------------------------------- | ----------------------------- |
| `POST`   | `/rooms`                                | Create a direct or group room |
| `GET`    | `/rooms`                                | List the caller's rooms       |
| `GET`    | `/rooms/{roomId}`                       | Get a room                    |
| `GET`    | `/rooms/{roomId}/participants`          | List room participants        |
| `DELETE` | `/rooms/{roomId}/participants/{userId}` | Leave or remove a participant |

### Messages

| Method   | Endpoint                   | Description           |
| -------- | -------------------------- | --------------------- |
| `POST`   | `/rooms/{roomId}/messages` | Send a message        |
| `GET`    | `/rooms/{roomId}/messages` | Get message history   |
| `PATCH`  | `/messages/{messageId}`    | Edit a message        |
| `DELETE` | `/messages/{messageId}`    | Soft-delete a message |

### Reactions

| Method   | Endpoint                         | Description              |
| -------- | -------------------------------- | ------------------------ |
| `PUT`    | `/messages/{messageId}/reaction` | Add or update a reaction |
| `DELETE` | `/messages/{messageId}/reaction` | Remove own reaction      |

### Read Cursors

| Method | Endpoint                      | Description                       |
| ------ | ----------------------------- | --------------------------------- |
| `PUT`  | `/rooms/{roomId}/read-cursor` | Update the caller's read position |

### Invitations

| Method | Endpoint                             | Description              |
| ------ | ------------------------------------ | ------------------------ |
| `POST` | `/rooms/{roomId}/invitations`        | Invite a user to a group |
| `POST` | `/invitations/{invitationId}/accept` | Accept an invitation     |
| `POST` | `/invitations/{invitationId}/reject` | Reject an invitation     |

### Internal

Internal endpoints are protected with a service-to-service token.

```text
PATCH /internal/messages/{messageId}/attachments
```

Used by the Attachment Service to attach uploaded file metadata to a message.

---

## Data Model

The service currently manages six main collections:

```text
ChatRoom
 ├── DIRECT
 └── GROUP

Participant
 ├── OWNER
 ├── ADMIN
 └── GUEST

Message
 ├── TEXT
 ├── IMAGE
 ├── VIDEO
 ├── AUDIO
 └── FILE

MessageReaction

ReadCursor

Invitation
 ├── PENDING
 ├── ACCEPTED
 └── REJECTED
```

Direct rooms use a deterministic participant key to prevent duplicate conversations between the same users.

Messages are soft-deleted rather than physically removed.

---

## Pagination

List endpoints use **cursor-based pagination** instead of offset pagination.

This is used for:

* Room lists
* Message history
* Other potentially growing collections

Message history is ordered using the message creation timestamp and MongoDB `_id`, providing a stable cursor even when multiple messages have the same timestamp.

---

## Authentication & Authorization

The service uses Auth-issued JWTs and validates them locally through the Auth Service JWKS endpoint.

The authenticated user's UUID always comes from the JWT `sub` claim.

```text
Client
  │
  │ Bearer JWT
  ▼
Chat Service
  │
  ├── Validate JWT
  │
  ├── Resolve caller from `sub`
  │
  ├── Check room membership
  │
  ├── Check participant role
  │
  └── Apply operation
```

Room operations are restricted to participants, with additional permissions for owners and administrators.

Direct messaging also checks the block relationship through the User Service.

---

## Getting Started

### Requirements

* Java 21
* Docker
* Maven (or the included Maven Wrapper)
* MongoDB 8

### Environment Configuration

Copy the example environment file and configure the required variables:

```bash
cp .env.example .env
```

Then update `.env` with your local configuration if needed.

> **Note:** `.env` contains environment-specific values and should not be committed. Use `.env.example` as the template for required variables.

### Start MongoDB

The local MongoDB instance runs as a single-node replica set:

```bash
docker compose up -d
```

The replica set is required for the multi-document transactions used by room and invitation operations.

### Run the service

```bash
./mvnw spring-boot:run
```

The service will be available at:

```text
http://localhost:8083
```

### Run tests

```bash
./mvnw test
```

The current test suite does not require an external database or Docker daemon.

---

## Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/ziyadsamhaoui/messagingchatservice/
│   │       ├── controller/
│   │       ├── service/
│   │       ├── repository/
│   │       ├── model/
│   │       ├── dto/
│   │       ├── client/
│   │       ├── config/
│   │       ├── security/
│   │       └── exception/
│   └── resources/
│       └── application.yml
└── test/
    └── java/
```

---

## Testing

The service currently contains **61 tests** covering:

* Authentication and authorization
* Room access rules
* Sender impersonation protection
* Block enforcement
* Direct room deduplication
* Invitation expiration
* Message validation
* Mute and participant rules
* MongoDB document mapping
* Repository and persistence behavior

The tests currently run without a live MongoDB instance. Database-backed integration testing against the MongoDB replica set is a planned follow-up.

---

## Limitations

The following features are intentionally outside the current scope:

* **No WebSocket/STOMP endpoint** — real-time delivery is handled by the Realtime Gateway.
* **No binary attachment uploads** — the service only provides an internal endpoint for attachment metadata.
* `isFavorited` is stored but cannot currently be modified through the API.
* `nickname` is stored for participants but is not writable yet.
* Reactions can be added, updated, or removed, but there is currently no dedicated endpoint for retrieving them.
* No typing indicators.
* No per-user room mute preferences.
* No unread message counters beyond the read cursor.
* Room lists are ordered by room creation time rather than last activity.

These limitations are part of the current sprint scope and are expected to be addressed by later BadrLink services or iterations.
