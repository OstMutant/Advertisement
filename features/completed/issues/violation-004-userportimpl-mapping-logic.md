---
type: violation
module: user-spring-boot-starter
priority: low
---

# violation-004: UserPortImpl contains DTO mapping logic

## Problem

`UserPortImpl` (`user-spring-boot-starter/.../spi/UserPortImpl.java`) violates the pure-delegation rule for `*PortImpl` classes. Three specific issues:

1. **`getFiltered()`** — calls `userService.getFiltered()` which returns `List<User>` entities, then maps them inline via `.stream().map(UserPortImpl::toDto).toList()`. The port performs the entity→DTO transformation instead of the service.

2. **`findActorNames()`** — contains a type-coercion ternary `ids instanceof Set<Long> s ? s : new HashSet<>(ids)` before delegating to the repository. Input normalization belongs in the service, not the port.

3. **`toDto(User user)`** — static entity→DTO mapping method lives in the port class. All DTO construction logic must live in the service layer.

## Expected behaviour

Each method in `UserPortImpl` should call exactly one service method and return the result directly. No stream pipelines, no mapping, no conditional logic.

## Fix

- Add `UserService.getFilteredDtos(UserFilterDto, Sort)` — returns `List<UserDto>` directly
- Add `UserService.findActorNameMap(Collection<Long>)` — accepts any Collection, handles normalization internally, returns `Map<Long, String>`
- Move `toDto(User)` into `UserService` (private)
- `UserPortImpl` methods become single-line delegations
