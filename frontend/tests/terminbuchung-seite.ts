import type { Locator, Page } from '@playwright/test'

export class TerminbuchungSeite {
  readonly slotIdFeld: Locator
  readonly patientennameFeld: Locator
  readonly grundFeld: Locator
  readonly erfolgsmeldung: Locator
  readonly fehlermeldung: Locator

  constructor(private readonly page: Page) {
    this.slotIdFeld = page.getByLabel('Termin-ID')
    this.patientennameFeld = page.getByLabel('Patientenname')
    this.grundFeld = page.getByLabel('Grund des Besuchs')
    this.erfolgsmeldung = page.getByRole('status')
    this.fehlermeldung = page.getByRole('alert')
  }

  async oeffnen() {
    await this.page.goto('/')
  }

  freierSlot(arzt: string): Locator {
    return this.bereich('Freie Termine').getByRole('row').filter({ hasText: arzt })
  }

  meinTermin(patient: string): Locator {
    return this.bereich('Meine Termine').getByRole('row').filter({ hasText: patient })
  }

  async slotAuswaehlen(arzt: string) {
    await this.freierSlot(arzt).getByRole('button', { name: 'Auswaehlen' }).click()
  }

  async buchen(slotId: string, patient: string, grund = 'Kontrolle') {
    await this.slotIdFeld.fill(slotId)
    await this.buchenMitAusgewaehltemSlot(patient, grund)
  }

  async buchenMitAusgewaehltemSlot(patient: string, grund = 'Kontrolle') {
    await this.patientennameFeld.fill(patient)
    await this.grundFeld.fill(grund)
    await this.page.getByRole('button', { name: 'Termin buchen' }).click()
  }

  async stornieren(patient: string) {
    await this.meinTermin(patient).getByRole('button', { name: 'Stornieren' }).click()
  }

  private bereich(ueberschrift: string): Locator {
    return this.page.locator('section').filter({ has: this.page.getByRole('heading', { name: ueberschrift }) })
  }
}
