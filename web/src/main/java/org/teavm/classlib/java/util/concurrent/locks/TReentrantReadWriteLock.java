package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Both views share one reentrant lock. Readers exclude each other too, which is
 * correct (just less concurrent) and fine for cooperative green threads.
 */
public class TReentrantReadWriteLock implements ReadWriteLock, java.io.Serializable {
    private final ReentrantLock lock = new ReentrantLock();
    private final ReadLock readerLock = new ReadLock(this);
    private final WriteLock writerLock = new WriteLock(this);

    public TReentrantReadWriteLock() { }
    public TReentrantReadWriteLock(boolean fair) { }

    @Override public ReadLock readLock() { return readerLock; }
    @Override public WriteLock writeLock() { return writerLock; }

    public boolean isWriteLocked() { return lock.isLocked(); }
    public boolean isWriteLockedByCurrentThread() { return lock.isHeldByCurrentThread(); }
    public int getReadHoldCount() { return lock.getHoldCount(); }
    public int getWriteHoldCount() { return lock.getHoldCount(); }

    public static class ReadLock implements Lock, java.io.Serializable {
        private final ReentrantLock lock;

        protected ReadLock(TReentrantReadWriteLock owner) { lock = owner.lock; }

        @Override public void lock() { lock.lock(); }
        @Override public void lockInterruptibly() throws InterruptedException { lock.lockInterruptibly(); }
        @Override public boolean tryLock() { return lock.tryLock(); }
        @Override public boolean tryLock(long t, TimeUnit u) throws InterruptedException { return lock.tryLock(t, u); }
        @Override public void unlock() { lock.unlock(); }
        @Override public Condition newCondition() { throw new UnsupportedOperationException(); }
    }

    public static class WriteLock implements Lock, java.io.Serializable {
        private final ReentrantLock lock;

        protected WriteLock(TReentrantReadWriteLock owner) { lock = owner.lock; }

        @Override public void lock() { lock.lock(); }
        @Override public void lockInterruptibly() throws InterruptedException { lock.lockInterruptibly(); }
        @Override public boolean tryLock() { return lock.tryLock(); }
        @Override public boolean tryLock(long t, TimeUnit u) throws InterruptedException { return lock.tryLock(t, u); }
        @Override public void unlock() { lock.unlock(); }
        @Override public Condition newCondition() { return lock.newCondition(); }
        public boolean isHeldByCurrentThread() { return lock.isHeldByCurrentThread(); }
        public int getHoldCount() { return lock.getHoldCount(); }
    }
}
