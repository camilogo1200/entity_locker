package locker

import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger

sealed interface IEntityLocker<T> {
    fun execute(
        entityId: T,
        protectedCode: () -> Unit,
        requiresGlobalLock: Boolean = false,
        taskName: String = ""
    ): Unit

    fun shutdown(immediate: Boolean): Unit
}

data class EntityLocker<T>(val maxThreads: Int? = null) : IEntityLocker<T> {
    private val availableProcessors by lazy { Runtime.getRuntime().availableProcessors() }
    private val lockGuard = ConcurrentHashMap<T, ExecutorService>()
    private val locks by lazy { ConcurrentHashMap<T, Lock>() }
    private var threads = maxThreads?.let { maxThreads } ?: availableProcessors;
    private val globalLock: ReentrantLock = ReentrantLock()
    private val globallyLockedCondition = globalLock.newCondition()
    private val globallyWaitingCondition = globalLock.newCondition()

    val defaultThreadPoolSize = threads
    val currentThreadPoolExecutors
        get() = lockGuard.values.size
    val currentLocks
        get() = locks.values.size

    val isGloballyLocked: Boolean
        get() = globalLock.isLocked

    fun getCurrentLocks(): HashMap<T, Int> {
        val map = hashMapOf<T, Int>()
        locks.onEach {
            map.putIfAbsent(it.key, 0)
            map[it.key] = map[it.key]?.plus(1) ?: 0
        }
        return map
    }

    fun getCurrentLocksPerEntity(idEntity: T): Int {
        return locks.filter { it.key == idEntity }.count()
    }


    override
    fun execute(entityId: T, protectedCode: () -> Unit, requiresGlobalLock: Boolean, taskName: String) {
        val newTask = createNewTask(entityId, protectedCode, requiresGlobalLock, taskName)
        synchronized(lockGuard) {
            addNewTask(newTask)
        }
    }

    private fun createNewTask(
        entityId: T,
        protectedCode: () -> Unit,
        requiresGlobalLock: Boolean,
        taskName: String
    ): LockerTask<T> {
        val lock: Lock = locks.getOrDefault(entityId, ReentrantLock())
        locks[entityId] = lock
        return LockerTask.newInstance(
            entityId,
            protectedCode,
            lock,
            globalLock,
            requiresGlobalLock,
            taskName,
            globallyLockedCondition,
            globallyWaitingCondition
        )
    }

    private fun addNewTask(newTask: LockerTask<T>) {
        val executor: ExecutorService =
            //lockGuard.getOrDefault(newTask.entityId, Executors.newCachedThreadPool())
            lockGuard.getOrDefault(newTask.entityId, Executors.newFixedThreadPool(threads))
        //lockGuard.getOrDefault(newTask.entityId, Executors.newSingleThreadExecutor())
        executor.submit(newTask)
        lockGuard[newTask.entityId] = executor
    }

    override fun shutdown(immediate: Boolean) {
        lockGuard.values.onEach {
            if (immediate) it.shutdownNow() else it.shutdown()
        }
    }

    fun clear() {
        instance = null
    }

    companion object {
        fun <T> getInstance(maxThreads: Int? = null): IEntityLocker<T> {
            if (instance == null) {
                instance = EntityLocker<Any>(maxThreads)
            }
            return instance!! as IEntityLocker<T>
        }

        private var instance: IEntityLocker<Any>? = null
    }
}

data class LockerTask<T>(
    val entityId: T,
    val block: () -> Unit,
    val lock: Lock,
    val globalLock: ReentrantLock,
    val requiresGlobalLock: Boolean = false,
    val globallyLockedCondition: Condition,
    val globallyWaitingCondition: Condition,
    var taskName: String? = null,
    val timeOut: Long = MAX_TIME_OUT,
    val pollTime: Long = MIN_POLL_TIME,
    val fairness: Boolean = true, //in favour of performance default => false
    val logLevel: Level = Level.CONFIG
) : Runnable {
    private var createdTime: Long = System.currentTimeMillis()
    private val logger = Logger.getLogger(LockerTask::class.java.name)
    override fun run() {
        lockEntity()
        try {
            block.invoke()
            logger.log(logLevel, "== Completed Task - [$taskName]. ==")
        } finally {
            releaseEntity()
        }
    }

    private fun releaseEntity() {
        if (requiresGlobalLock) {
            globalLock.unlock()
            //globallyLockedCondition.signalAll()
        }
        logger.log(logLevel, "Releasing Lock - [$taskName]")
        lock.unlock()
    }

    private fun lockEntity() {
        taskName = if (taskName == null) Thread.currentThread().name else taskName
        try {
            verifyLockConditions()
            lock.lock()
            val waitedTime = System.currentTimeMillis() - createdTime
            logger.log(logLevel, "Lock -Task [$taskName] executing now, waited ${waitedTime}ms")
        } catch (ex: Exception) {
            val message = ex.message
            logger.log(Level.SEVERE, "Exception on Task [$taskName].")
        }
    }

    private fun verifyLockConditions() {
        if (requiresGlobalLock) {
            while (!globalLock.tryLock()) {
                logger.log(logLevel, "Awaiting to Lock global - Thread [$taskName]")
                //globallyLockedCondition.await() //didn't work due to the lock-owner condition
                Thread.sleep(pollTime)
            }
        } else {
            while (globalLock.isLocked) {
                logger.log(logLevel, "Waiting global Lock for execution on Thread [$taskName]")
                //globallyLockedCondition.await() //didn't work due to the lock-owner condition
                Thread.sleep(pollTime)
            }
        }
    }

    companion object {
        private lateinit var lock: ReentrantLock
        const val MAX_TIME_OUT = 10_000L
        const val MIN_POLL_TIME = 50L

        fun <T> newInstance(
            entityId: T,
            block: () -> Unit,
            lock: Lock,
            globalLock: ReentrantLock,
            requiresGlobalLock: Boolean = false,
            taskName: String = "",
            globallyLockedCondition: Condition,
            globallyWaitingCondition: Condition,
            timeOut: Long = MAX_TIME_OUT,
            pollTime: Long = MIN_POLL_TIME,
            fairness: Boolean = false
        ): LockerTask<T> {
            return LockerTask(
                entityId,
                block,
                lock,
                globalLock,
                requiresGlobalLock,
                globallyLockedCondition,
                globallyWaitingCondition,
                taskName,
                timeOut,
                pollTime,
                fairness
            )
        }

    }
}