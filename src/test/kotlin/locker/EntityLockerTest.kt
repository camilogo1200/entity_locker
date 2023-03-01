package locker

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.w3c.dom.ranges.Range
import java.util.concurrent.CountDownLatch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

data class TestClass(var value: Int, val id: Int)
class EntityLockerTest {

    companion object {
        const val SHORT_RUNNING_DB_OPERATION = 50L
        const val LONG_RUNNING_DB_OPERATION = 5000L
        const val SUPER_LONG_RUNNING_DB_OPERATION = 10000L
        const val THREADS_32 = 32
    }

    /*
        @Rule
        val concurrentlyRule = ConcurrentRule()

        @Rule
        val repeatingRule = RepeatingRule()
    */
    val latch = CountDownLatch(10)

    private fun TestClass.incrementValue(delay: Boolean = true) {
        if (delay)
            Thread.sleep(SHORT_RUNNING_DB_OPERATION)
        value += 1
    }

    private fun expensiveTimeFunction() {
        Thread.sleep(SUPER_LONG_RUNNING_DB_OPERATION)
    }

    private var entityLocker: IEntityLocker<Int>? = null

    @BeforeTest
    fun setUp() {
        entityLocker = EntityLocker.getInstance<Int>()
    }

    @AfterTest
    fun tearDown() {
        (entityLocker as EntityLocker).clear()
        entityLocker = null
    }

    @Test
    fun `static method should create Locker instance with no Executors and no locks`() {
        assertNotNull(entityLocker)
        assertInstanceOf(EntityLocker::class.java, entityLocker)
        val eLocker = entityLocker as EntityLocker<Int>
        assertEquals(0, eLocker.currentLocks)
        assertEquals(0, eLocker.currentThreadPoolExecutors)
    }

    @Test
    fun `should create Locker instance with no Executors and no locks`() {
        val eLocker = entityLocker as EntityLocker
        assertNotNull(eLocker)
        assertEquals(0, eLocker.currentLocks)
        assertEquals(0, eLocker.currentThreadPoolExecutors)
    }

    @Test
    fun `should create executor threads with parametrized thread amount`() {
        val eLocker = EntityLocker.getInstance<String>(THREADS_32) as EntityLocker
        assertNotNull(eLocker)
        assertEquals(THREADS_32, eLocker.defaultThreadPoolSize)
    }

    @Test

    fun `should create and execute task for an entity`() {
        val entity = TestClass(0, 1)

        entityLocker?.execute(entity.id, { entity.incrementValue() }, true, "GlobalLock-1")

        Thread.sleep(2000)
        val eLocker = entityLocker as EntityLocker
        assertNotNull(eLocker)
        assertEquals(1, entity.value)
        assertEquals(1, eLocker.currentLocks)
        assertEquals(1, eLocker.currentThreadPoolExecutors)
    }

    @Test
    fun `should execute task in order for the same entity`() {

    }

    @Test
    fun `should execute task and lock globally`() {
        val entity = TestClass(0, 1)

        entityLocker?.execute(entity.id, { expensiveTimeFunction() }, true, "GlobalLock-1")

        val eLocker = entityLocker as EntityLocker
        Thread.sleep(2000)
        assertNotNull(eLocker)
        assertTrue(eLocker.isGloballyLocked)
        assertEquals(1, eLocker.currentLocks)
        assertEquals(1, eLocker.currentThreadPoolExecutors)
    }


    @Test
    fun `should execute 10 task and increment value to 10 for the same entity`() {
        val entity = TestClass(0, 1)
        for (i in 0 until 10 step 1) {
            entityLocker?.let {
                it.execute(entity.id, { entity.incrementValue() }, taskName = "I-1")
            }
        }
        Thread.sleep(2000)
        assertEquals(10, entity.value)
    }

    @Test
    fun `should execute 10 task and increment value to 10 on different entities`() {
        val entity = TestClass(0, 1)
        val entity2 = TestClass(0, 2)
        val entity3 = TestClass(0, 3)

        (0..9).onEach {
            entityLocker?.execute(entity.id, { entity.incrementValue(false) }, taskName = "I-1")
            entityLocker?.execute(entity2.id, { entity2.incrementValue(false) }, taskName = "I-2")
            entityLocker?.execute(entity3.id, { entity3.incrementValue(false) }, taskName = "I-3")
        }

        Thread.sleep(3000)
        assertEquals(10, entity.value)
        assertEquals(10, entity2.value)
        assertEquals(10, entity3.value)
    }

    //@Concurrent(count = 10)
    //@Repeating(repetition = 2)
    //behaviour test
    @Test
    fun `should create and execute multiple task for different entities simultaneously`() {
        assertTrue(false)
    }


    @Test
    fun `should lock current threads executions when global lock is true task are global`() {
        assertTrue(false)
    }
}