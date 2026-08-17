package fr.easter.brewhome

import fr.easter.brewhome.calc.CalendarEvents
import fr.easter.brewhome.notif.BrewReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BrewRemindersTest {

    private fun event(type: CalendarEvents.Type, dryhopDone: Boolean = false) = CalendarEvents.Event(
        date = LocalDate.of(2026, 8, 1), type = type, label = "Test", emoji = "🍺", dryhopDone = dryhopDone,
    )

    @Test
    fun `brassage et mise en bouteille sont notifiables`() {
        val reminders = BrewReminders.remindersFrom(
            listOf(event(CalendarEvents.Type.BREW), event(CalendarEvents.Type.BOTTLE)),
        )
        assertEquals(2, reminders.size)
        assertTrue(reminders.any { it.title.contains("Brassage prévu") })
        assertTrue(reminders.any { it.title.contains("Mise en bouteille prévue") })
    }

    @Test
    fun `une journee mondiale elle-meme n'est pas notifiee (seul son rappel REMIND l'est)`() {
        val reminders = BrewReminders.remindersFrom(listOf(event(CalendarEvents.Type.WORLD)))
        assertTrue(reminders.isEmpty())
    }

    @Test
    fun `un dry hop deja fait n'est pas notifie`() {
        val reminders = BrewReminders.remindersFrom(listOf(event(CalendarEvents.Type.DRYHOP, dryhopDone = true)))
        assertTrue(reminders.isEmpty())
    }

    @Test
    fun `un brouillon n'est pas notifiable`() {
        val reminders = BrewReminders.remindersFrom(listOf(event(CalendarEvents.Type.DRAFT)))
        assertTrue(reminders.isEmpty())
    }
}
