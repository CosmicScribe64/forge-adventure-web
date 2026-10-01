---
type: concept
sources: [NOTES.md#round-3, NOTES.md#round-5, NOTES.md#round-8, NOTES.md#round-9, NOTES.md#review, web/src/main/java/forgeweb/compat/UiThread.java, web/src/main/java/org/teavm/classlib/java/lang/TObject.java]
updated: 2026-10-01
tags: [threads, teavm, concurrency]
---

# Green threads and the UI thread

[[forge|Forge]] is written for real threads. It blocks its UI thread (for example
`World.generateNew` joins `CompletableFuture`s, and `AudioClip` sleeps 30 ms in click handlers),
and it syncs game and input through latches, blocking deques and executors. [[teavm|TeaVM]]
emulates `Thread`, `wait`/`notify` and `sleep` as **green threads**: coroutines that switch
only at suspension points, inside one JS thread. This page covers how the port works within that.

## The core constraint

Browser callbacks (animation frames, input events, timers) run with **no green thread**. Any
wait, sleep or contended lock there throws "Suspension point reached from non-threading
context". Code *on* a green thread can block freely, and the page keeps running.

## How the port handles it

- **One UI green thread** (`forgeweb.compat.UiThread`, "Forge UI"). Every lifecycle call,
  input event and `Gdx.app.postRunnable` task runs there, in delivery order. Browser callbacks
  only queue work (`post`, `postFrame`). While a task blocks, new frames are **dropped** (at
  most one waits in the queue), counted by `FrameStats`. See [[one-ui-green-thread]].
- **`MainThread*`** (`MainThread`, `MainThreadListener`, `MainThreadInputProcessor`) route
  the libGDX listener and input processor onto it. `MainThread` tells whether the current call
  stack can suspend.
- **Stale current thread** (Round 3, a TeaVM bug): after a green thread suspends,
  `Thread.currentThread()` could be stale, so Forge thought frames weren't on the UI thread.
- **`Thread.sleep`** is redirected to `JdkCompat.sleep`, which skips the pause where it can't
  suspend.
- **`availableProcessors() == 1`**, so Forge loads cards inline instead of through a pool. See
  [[single-processor]].
- **Concurrency classlib shadows**: `TCountDownLatch`, `TCompletableFuture`, `TFutureTask`,
  `TGreenThreadExecutor`, `TReentrantLock`, `TLinkedBlockingDeque` (TeaVM's own is a plain
  LinkedList, so Forge's `InputQueue.push` was never compiled), and others.
- **`Thread.getStackTrace`** returns placeholder frames. Forge indexes `trace[2]` in
  `FThreads.assertExecutedByEdt`, and TeaVM has no bounds checks.

## Synchronized methods and "borrowed" monitors

TeaVM compiles a small `synchronized` method that can't suspend with a *non-blocking* monitor
entry. In stock TeaVM, if another green thread holds that lock (because it is parked at a
suspension point inside its own synchronized block), the entry **throws**. That crashed
conceding a duel: `Game.isGameOver()` was entered while `setGameOver` held the lock and fired
events.

The fix is shadowed `TObject` (`org/teavm/classlib/java/lang/TObject.java`, a TeaVM 0.15 copy
with one change). In the contended non-suspending case, the caller runs **without taking
ownership** and increments `monitor.borrowed`. This is sound because green threads can't
interleave, so the owner can't run until the caller finishes. The one caveat is that the caller may see the
owner's half-done update. A borrowed section counts as holding the lock for
`notify()` and `holdsLock()`. It logs `[monitor] entered a lock held by a suspended thread: <class>`
up to 20 times.

> [!note] Superseded
> Round 8 fixed conceding with a Forge patch (a lock-free volatile `isGameOver`). The
> borrowed monitor made that unnecessary, and the patch is gone as of 2026-09-29 (see
> [[src-forge-web-patch]]). PLAN Phase 3 still lists a transformer that drops `synchronized`
> from non-suspending methods. Whether that is still needed is an open question in
> [[plan-phases]].

## Hazard: lost wake-ups

A green thread that yields *after* posting work can miss the `notify()` of a quick task. For
example, `Progress` yielded right after `FThreads.invokeInEdtLater`, the EDT task notified
before `WaitCallback.invokeAndWait` waited, and the game hung silently. The rule is to yield
**before** posting. (Found in the 2026-09-29 review.)

## Verified by
[[selftest]] checks: sleep from a browser callback, UI-thread task blocking on a
CompletableFuture, an executor with a CountDownLatch, a failing task through `Future.get`, BlockingDeque,
`getStackTrace`, and a small synchronized method while another thread is suspended holding the
lock.

## See also
[[teavm-gotchas]] · [[bug-catalog]] · [[world-generation]] (the calling green thread waits on workers)
