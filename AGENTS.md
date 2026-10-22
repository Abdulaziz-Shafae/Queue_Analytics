# Shefo Coding Style — Java / Spring Boot

> Version 2.2 • 20 October 2026
> Reference: user-provided `Capstone_3.zip`, `session.pdf`, and `relations and validations.pdf`.
> This is the **preferred style for future work**; parts of the example project do not consistently follow every rule below.

## NON-NEGOTIABLE RULES

1. **NEVER split a single logical Java code line into multiple physical lines.** Keep the entire method declaration/signature (including all parameters), method call, `return`, `ResponseEntity` chain, condition, annotation, query, assignment, and lambda expression on **one source line**, even when long. Prefer horizontal scrolling over hard wrapping. Ordinary line breaks **between statements and blocks** are required. Keep normal indentation and braces.
2. **Each function needs a descriptive name that states what it does.** Use an action verb plus a target: `createSession`, `acceptExchange`, `getSessionsByOffer`, `updateAttendance`, `getExchangeDetails`, `sendVerificationEmail`. Avoid vague new method names such as `doIt`, `process`, `handle`, `execute`, `test`, `method1`. Existing CRUD endpoints can retain their original names when keeping compatibility.
3. **Follow the existing project package names and structure**, including capitalization: `Advice`, `Api`, `Controller`, `Service`, `DtoIn`, `DtoOut`, `Repository`. The provided Capstone 3 also uses `Model` and `Config`; retain these where the project needs them. Do not flatten layers into one class.
4. **Make targeted changes only.** Do not rename paths, parameters, DTOs, model fields, entities, or methods unrelated to the request. Keep existing Postman/Frontend contracts intact unless explicitly asked to change them.
5. **For projects without completed authentication/security, take the acting user/account ID from the URL using `@PathVariable`**, not `HttpSession`, `@AuthenticationPrincipal`, `Principal`, or `SecurityContextHolder`. Explicitly include `{userId}` or `{accountId}` in the relevant route. Keep business rules and ownership checks in the service. When real Spring Security is implemented later, obtain the acting identity from the authenticated principal instead of trusting the path ID. Do not interpret path IDs as proof of identity or authorization.
6. When suggesting changes interactively, **name the exact file, show the full replacement/insert snippet, and explain exactly what changed**. When the user asks to go step by step, give one change at a time.

## Required project layout

```text
src/main/java/com/example/<project>/
├── Advice/
│   └── ControllerAdvice.java
├── Api/
│   ├── ApiException.java
│   └── ApiResponse.java
├── Controller/
│   ├── SessionController.java
│   └── ExchangeController.java
├── Service/
│   ├── SessionService.java
│   └── ExchangeService.java
├── DtoIn/
│   └── CreateSessionDtoIn.java
├── DtoOut/
│   └── ExchangeDtoOut.java
├── Repository/
│   ├── SessionRepository.java
│   └── ExchangeRepository.java
├── Model/                # JPA entities and relationships when needed
└── Config/               # Configurations/integrations when needed
```

**Responsibilities**

- `Advice`: global exception handlers (`ControllerAdvice`), translate failures into safe HTTP responses.
- `Api`: shared `ApiException` and `ApiResponse` types.
- `Controller`: request mapping, request validation, reading acting user/account ID from `@PathVariable` during pre-security development, invoking exactly the appropriate service function, returning `ResponseEntity`. Avoid large business logic here.
- `Service`: all business rules, authorization, database workflow, transactions, and integrations via dedicated services.
- `DtoIn`: input contract fields and Jakarta validation annotations.
- `DtoOut`: response contract fields; do not leak passwords, secrets, or internal relationship graphs.
- `Repository`: `JpaRepository` interfaces and descriptive database query methods.
- `Model` (when needed): JPA `@Entity`, column constraints, relationships and persistent fields.
- `Config` (when needed): config beans and external service configuration; no hardcoded credentials.

## Formatting: NO broken code lines

### Do this

```java
@PostMapping("/create/{accountId}/{offerId}")
public ResponseEntity<?> createSession(@PathVariable Integer accountId, @PathVariable Integer offerId, @RequestBody @Valid CreateSessionDtoIn createSessionDtoIn) {
    return ResponseEntity.status(201).body(sessionService.createSession(accountId, offerId, createSessionDtoIn));
}
```

```java
@GetMapping("/details/{accountId}/{exchangeId}")
public ResponseEntity<?> getExchangeDetails(@PathVariable Integer accountId, @PathVariable Integer exchangeId) {
    return ResponseEntity.status(200).body(exchangeService.getExchangeDetails(accountId, exchangeId));
}
```

### Never do this

```java
public ResponseEntity<?> createSession(
        @PathVariable Integer accountId,
        @PathVariable Integer offerId,
        @RequestBody @Valid CreateSessionDtoIn createSessionDtoIn) {
    return ResponseEntity.status(201)
            .body(sessionService.createSession(accountId, offerId, createSessionDtoIn));
}
```

**Important:** This rule is about *physical source lines*. You may still put each separate statement on its own line, and each `if`/`for`/`try` block may span multiple lines. Do not compress an entire function into one line. Editors may visually soft-wrap long lines without inserting a newline; do not intentionally insert hard line breaks to fit the screen.

## Naming

| Element | Standard | Example |
| --- | --- | --- |
| Class/interface | PascalCase | `SessionService`, `SessionRepository` |
| Variable/field | camelCase | `sessionId`, `exchangeRepository` |
| Function | verb + descriptive noun | `joinSession()`, `cancelExchange()` |
| Incoming DTO | `SomethingDtoIn` | `CreateSessionDtoIn` |
| Outgoing DTO | `SomethingDtoOut` | `ExchangeDtoOut` |
| Constant/enum value | UPPER_SNAKE_CASE | `IN_PROGRESS`, `ONLINE` |
| REST path | existing `/api/v1/...` conventions, acting ID in path before security | `/api/v1/session/create/{accountId}/{offerId}` |

Avoid creating new ambiguous functions named `get()`, `add()`, `update()`, `delete()` unless explicitly implementing the project's established CRUD pattern. **Do not rename existing CRUD functions without an explicit request**, because compatibility matters.

## Controller pattern (from Capstone 3)

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/exchange")
public class ExchangeController {
    private final ExchangeService exchangeService;

    @PutMapping("/{accountId}/{exchangeId}/accept")
    public ResponseEntity<?> acceptExchange(@PathVariable Integer accountId, @PathVariable Integer exchangeId) {
        exchangeService.acceptExchange(accountId, exchangeId);
        return ResponseEntity.status(200).body(new ApiResponse("Exchange accepted successfully"));
    }
}
```

- Use `@Valid` on a DTO request body when validation exists.
- Before Spring Security is implemented, read the acting identity with `@PathVariable Integer userId` or `@PathVariable Integer accountId` in the controller, and pass that `Integer` to the service. Prefer `userId` for projects whose domain model is `User`, and `accountId` for projects whose domain model is `Account`. Never mix the two without a deliberate domain-model change.
- Do not add `HttpSession`, `@AuthenticationPrincipal`, `Principal`, or `SecurityContextHolder` to these pre-security controllers; do not store or read the acting ID from an HTTP session. A domain entity named `Session` (such as a scheduled class/session) is unrelated to `HttpSession` and should remain unchanged.
- Passing an ID in the URL is a temporary development convention, not authentication. Preserve service-side existence and ownership validations, but never claim those checks can prove who sent the request. On implementing actual Spring Security, retrieve identity from the authenticated principal and recheck authorization server-side. Only change routes/contracts for this when explicitly asked.
- Use short controllers but do not force a single-line **method body**; only keep each statement/condition/signature unbroken.
- Preserve established `ResponseEntity.status(...).body(...)`, `ApiResponse`, route names and statuses where compatibility requires it.

## Service pattern

```java
@Service
@RequiredArgsConstructor
public class SkillOfferService {
    private final SkillOfferRepository skillOfferRepository;

    public SkillOffer getSkillOfferById(Integer offerId) {
        SkillOffer skillOffer = skillOfferRepository.findSkillOfferById(offerId);
        if (skillOffer == null) {
            throw new ApiException("Skill offer not found");
        }
        return skillOffer;
    }
}
```

The sample above is illustrative, not a claim that this exact function exists in Capstone 3. In real features, validate login/roles, existence and ownership; apply the business rules; save via repository; return the appropriate response type. Add `@Transactional` for coordinated DB operations when appropriate. Handle external email, WhatsApp, Zoom and AI side effects deliberately: DB rollback does not automatically undo an external API call.

## DtoIn / DtoOut

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionDtoIn {
    @NotNull(message = "Negotiation ID is required")
    @Positive(message = "Negotiation ID must be positive")
    private Integer negotiationId;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least one minute")
    private Integer durationMinutes;
}
```

Use input DTOs for values allowed from clients, and output DTOs for safe response fields. The example's fields/validations are taken from the uploaded Capstone 3 `CreateSessionDtoIn` (excerpted for clarity). Name DTOs according to their specific purpose; don't use an entity as a generic request/response if doing so exposes unintended fields.

## Repository

```java
@Repository
public interface SessionRepository extends JpaRepository<Session, Integer> {
    boolean existsBySkillOffer_IdAndScheduledAtAndStatus(Integer offerId, LocalDateTime scheduledAt, String status);
}
```

Keep method signatures on one line. Name queries according to their purpose. Repositories fetch and store data; service owns the business decision.

## Advice and Api — use the user's ControllerAdvice by default

**Mandatory preference:** In new Java / Spring Boot projects, use the following user-supplied `ControllerAdvice` pattern by default. Keep all ten handlers, their method names, HTTP status codes, response messages, and `ApiResponse` / `ApiException` conventions unless the user explicitly requests changes. Change only the root package name to match the active project. Do not automatically replace this class with a different exception handling design.

```java
package com.example.capstone_3.Advice;

import com.example.capstone_3.Api.ApiException;
import com.example.capstone_3.Api.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@org.springframework.web.bind.annotation.ControllerAdvice
public class ControllerAdvice {

    @ExceptionHandler(value = ApiException.class)
    public ResponseEntity<?> ApiException(ApiException e) {
        return ResponseEntity.status(400).body(new ApiResponse(e.getMessage()));
    }

    //handle the server side validation
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.status(400).body(new ApiResponse(e.getBindingResult().getFieldError().getDefaultMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse> handleUnreadableMessage(HttpMessageNotReadableException e) {
        return ResponseEntity.status(400).body(new ApiResponse("Invalid or missing JSON request body. Check the JSON syntax, field types, and date formats"));
    }

    // handle if the entered id while he should not
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> handleWritingId(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(400).body(new ApiResponse(e.getMessage()));
    }

    //handle the database side constrain SQL
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(400).body(new ApiResponse(e.getMessage()));
    }

    //for invalid type
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(400).body(new ApiResponse("Invalid value for " + e.getName()));
    }

    //for invalid type for mapping
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleTypeMismatch(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(400).body(new ApiResponse(e.getMessage()));
    }

    //for invalid type for mapping
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleTypeMismatch(NoResourceFoundException e) {
        return ResponseEntity.status(400).body(new ApiResponse(e.getMessage()));
    }

    //handle database query or SQL execution errors
    @ExceptionHandler(InvalidDataAccessResourceUsageException.class)
    public ResponseEntity<ApiResponse> handleInvalidDataAccessResourceUsageException(InvalidDataAccessResourceUsageException exception) {
        return ResponseEntity.status(400).body(new ApiResponse("Database operation failed. Please contact support"));
    }

    //handle missing required request parameters
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.status(400).body(new ApiResponse("Missing required parameter: " + e.getParameterName()));
    }
}
```

The handler implementation above intentionally follows the **user's supplied behavior**, including generic 400 statuses and exposed exception messages in some handlers; do not silently revise the behavior. If asked to review security or HTTP semantics, flag any concerns separately and obtain approval before changing it.

```java
public class ApiException extends RuntimeException {
    public ApiException(String message) {
        super(message);
    }
}
```

## Model & validation reference

The provided `relations and validations.pdf` illustrates `@OneToOne`, `@ManyToMany`, `@OneToMany`, `@ManyToOne`, `@MapsId`, `@JsonIgnore`, `@NotBlank`, `@NotNull`, `@Size`, `@Pattern`, and database `@Check`. Use the relationship annotations appropriate to the *actual entity ownership/mapping* rather than copying placeholders. For example, do not blindly use `cascade = CascadeType.ALL` or a `@PrimaryKeyJoinColumn` without matching the real key strategy.

## Change review before response

- [ ] Did I follow the existing `Advice`, `Api`, `Controller`, `Service`, `DtoIn`, `DtoOut`, `Repository` folders?
- [ ] Did I keep **every method signature, method call, return, chain and annotation on a single physical line**?
- [ ] Does **every new method name clearly explain its purpose**?
- [ ] For pre-security controllers, did I take `Integer accountId`/`userId` from `@PathVariable` (and include it in the route), avoiding `HttpSession` and `@AuthenticationPrincipal`?
- [ ] Are business rules in Service, queries in Repository, validation in DTOs, errors in Advice/Api?
- [ ] Did I preserve ownership/business validations, while recognizing a path ID is not authenticated identity?
- [ ] Did I avoid unrelated refactors or breaking existing APIs and models?
- [ ] Did I show which files were changed and how to test them?

When asked to edit code, follow these preferences even if an automatic code formatter would normally wrap long method signatures or fluent chains. User instructions about unbroken lines take precedence over generic formatting advice.
