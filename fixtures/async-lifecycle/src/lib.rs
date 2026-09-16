/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

//! Async functions whose futures count their own construction and drop.

use parking_lot::Mutex;
use std::{
    future::Future,
    pin::Pin,
    sync::{
        Arc,
        atomic::{AtomicU64, Ordering},
    },
    task::{Context, Poll, Waker},
    thread,
    time::Duration,
};

uniffi::setup_scaffolding!("async_lifecycle");

static CREATED: AtomicU64 = AtomicU64::new(0);
static DROPPED: AtomicU64 = AtomicU64::new(0);

struct DropTracker;

impl DropTracker {
    fn new() -> Self {
        CREATED.fetch_add(1, Ordering::SeqCst);
        Self
    }
}

impl Drop for DropTracker {
    fn drop(&mut self) {
        DROPPED.fetch_add(1, Ordering::SeqCst);
    }
}

#[uniffi::export]
pub fn created_count() -> u64 {
    CREATED.load(Ordering::SeqCst)
}

#[uniffi::export]
pub fn dropped_count() -> u64 {
    DROPPED.load(Ordering::SeqCst)
}

#[uniffi::export]
pub fn reset_counts() {
    CREATED.store(0, Ordering::SeqCst);
    DROPPED.store(0, Ordering::SeqCst);
}

struct TimerState {
    completed: bool,
    waker: Option<Waker>,
}

/// Completes after `duration` on a spawned thread, after `spurious_wakes` wakes that leave the
/// future pending; the foreign side re-polls once per wake.
struct Timer {
    state: Arc<Mutex<TimerState>>,
}

impl Timer {
    fn new(duration: Duration, spurious_wakes: u16) -> Self {
        let state = Arc::new(Mutex::new(TimerState {
            completed: false,
            waker: None,
        }));
        let thread_state = Arc::clone(&state);
        // Wakers are invoked with the lock released: an inline foreign executor re-polls from
        // inside `wake()`, on this thread.
        thread::spawn(move || {
            for _ in 0..spurious_wakes {
                thread::sleep(Duration::from_millis(1));
                let waker = thread_state.lock().waker.take();
                if let Some(waker) = waker {
                    waker.wake();
                }
            }
            thread::sleep(duration);
            let waker = {
                let mut state = thread_state.lock();
                state.completed = true;
                state.waker.take()
            };
            if let Some(waker) = waker {
                waker.wake();
            }
        });
        Self { state }
    }
}

impl Future for Timer {
    type Output = ();

    fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<()> {
        let mut state = self.state.lock();
        if state.completed {
            Poll::Ready(())
        } else {
            state.waker = Some(cx.waker().clone());
            Poll::Pending
        }
    }
}

#[uniffi::export]
pub async fn tracked_ready() {
    let _tracker = DropTracker::new();
}

#[uniffi::export]
pub async fn tracked_sleep(ms: u16) {
    let _tracker = DropTracker::new();
    Timer::new(Duration::from_millis(ms.into()), 0).await;
}

#[uniffi::export]
pub async fn tracked_wake_storm(ms: u16, spurious_wakes: u16) {
    let _tracker = DropTracker::new();
    Timer::new(Duration::from_millis(ms.into()), spurious_wakes).await;
}

#[uniffi::export(with_foreign)]
#[async_trait::async_trait]
pub trait Notifier: Send + Sync {
    async fn on_ready(&self);
}

/// The sleep guarantees the foreign caller holds the future before `on_ready` is upcalled from
/// a re-poll.
#[uniffi::export]
pub async fn sleep_then_notify(ms: u16, notifier: Arc<dyn Notifier>) {
    let _tracker = DropTracker::new();
    Timer::new(Duration::from_millis(ms.into()), 0).await;
    notifier.on_ready().await;
}
