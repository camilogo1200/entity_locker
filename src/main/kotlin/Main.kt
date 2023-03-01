import locker.EntityLocker
import locker.IEntityLocker
import java.security.SecureRandom
import kotlin.concurrent.timer

fun main(args: Array<String>) {
    val locker: IEntityLocker<String> = EntityLocker.getInstance()
    val rnd = SecureRandom(SecureRandom().generateSeed(30))

    locker.execute("Task-1", {
        expensiveTask(" Task-Custom - [5segs]", 5000)
    }, true)

    for (i in 0 until 2 step 1) {
        var timeRnd = rnd.nextInt(30) * 100
        var taskName = "Task-1 - $i - [$timeRnd]"

        locker.execute("Entity-0", {
            expensiveTask(" Task-Custom - Global Lock [$timeRnd]", timeRnd.toLong())
        }, true)

        timeRnd = rnd.nextInt(30) * 100
        taskName = "Entity-1 - $i - [$timeRnd]"

        locker.execute("Entity-1", {
            expensiveTask(taskName, timeRnd.toLong())
        }, taskName = taskName)

        timeRnd = rnd.nextInt(30) * 100
        taskName = "Entity-2 - $i - [$timeRnd]"

        locker.execute("Entity-2", {
            expensiveTask(taskName, timeRnd.toLong())
        }, taskName = taskName)
    }
}

fun expensiveTask(name: String, time: Long) {
    Thread.currentThread().name = name
    println("Starting Thread [$name]")
    Thread.sleep(time);
    println("Done Thread [$name]")
}