import { expect, test } from '@playwright/test'
import { beschrifteFuerAllure } from './allure-labels'
import { bucheUeberApi, eindeutig, neuerSlot } from './hilfen'
import { TerminbuchungSeite } from './terminbuchung-seite'

// Playwright verlangt Objekt-Destrukturierung als erstes Argument, auch ohne Fixtures.
// oxlint-disable-next-line no-empty-pattern
test.beforeEach(async ({}, testInfo) => {
  await beschrifteFuerAllure(testInfo)
})

test(
  'Slot auswaehlen, buchen, stornieren: Slot ist wieder frei (Anforderung 1, 4)',
  { tag: ['@anf-1', '@anf-4'] },
  async ({ page, request }) => {
    const seite = new TerminbuchungSeite(page)
    const arzt = eindeutig('Dr. Ablauf')
    const patient = eindeutig('Patient')
    const slot = await neuerSlot(request, 72, arzt)

    await seite.oeffnen()
    await seite.slotAuswaehlen(arzt)
    await expect(seite.slotIdFeld).toHaveValue(slot.id)

    await seite.buchenMitAusgewaehltemSlot(patient, 'Jahreskontrolle')
    await expect(seite.erfolgsmeldung).toHaveText('Termin gebucht.')
    await expect(seite.meinTermin(patient)).toContainText('Jahreskontrolle')
    await expect(seite.freierSlot(arzt)).toHaveCount(0)

    await seite.stornieren(patient)
    await expect(seite.erfolgsmeldung).toHaveText('Termin storniert.')
    await expect(seite.freierSlot(arzt)).toBeVisible()
  },
)

test(
  'bereits gebuchter Slot: Fehlermeldung, keine Buchung (Anforderung 1)',
  { tag: '@anf-1' },
  async ({ page, request }) => {
    const seite = new TerminbuchungSeite(page)
    const slot = await neuerSlot(request, 72)
    await bucheUeberApi(request, slot.id, eindeutig('Erstbucher'))
    const patient = eindeutig('Zweitbucher')

    await seite.oeffnen()
    await seite.buchen(slot.id, patient)

    await expect(seite.fehlermeldung).toHaveText('Der Slot ist bereits gebucht.')
    await expect(seite.patientennameFeld).toHaveValue(patient)
    await expect(seite.meinTermin(patient)).toHaveCount(0)
  },
)

test(
  'Defect 10: "Meine Termine" zeigt Arzt und Beginn des Termins (Frontend)',
  { tag: '@Defect-10' },
  async ({ page, request }) => {
    const seite = new TerminbuchungSeite(page)
    const arzt = eindeutig('Dr. Anzeige')
    const patient = eindeutig('Patient')
    const slot = await neuerSlot(request, 72, arzt)
    await bucheUeberApi(request, slot.id, patient)

    await seite.oeffnen()
    const zellen = seite.meinTermin(patient).getByRole('cell')
    const erwarteterBeginn = await page.evaluate(
      (beginn) => new Date(beginn).toLocaleString('de-AT'),
      slot.beginn,
    )

    await expect.soft(zellen.nth(0)).toHaveText(arzt)
    await expect.soft(zellen.nth(1)).toHaveText(erwarteterBeginn)
  },
)

test(
  'Defect 11: stornierter Termin ist in "Meine Termine" als storniert erkennbar (Frontend)',
  { tag: '@Defect-11' },
  async ({ page, request }) => {
    const seite = new TerminbuchungSeite(page)
    const patient = eindeutig('Patient')
    const slot = await neuerSlot(request, 72)
    await bucheUeberApi(request, slot.id, patient)

    await seite.oeffnen()
    await seite.stornieren(patient)
    await expect(seite.erfolgsmeldung).toHaveText('Termin storniert.')

    await expect(seite.meinTermin(patient)).toContainText(/storniert/i)
    await expect(seite.meinTermin(patient).getByRole('button', { name: 'Stornieren' })).toHaveCount(0)
  },
)

test(
  'Defect 12: vergangene Slots werden nicht als frei angeboten (Frontend)',
  { tag: '@Defect-12' },
  async ({ page, request }) => {
    const seite = new TerminbuchungSeite(page)
    const arzt = eindeutig('Dr. Zeit')
    await neuerSlot(request, -1, `${arzt} gestern`)
    await neuerSlot(request, 72, `${arzt} morgen`)

    await seite.oeffnen()
    await expect(seite.freierSlot(`${arzt} morgen`)).toBeVisible()

    await expect(seite.freierSlot(`${arzt} gestern`)).toHaveCount(0)
  },
)
