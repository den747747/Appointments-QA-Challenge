import type { TestInfo } from '@playwright/test'
import * as allure from 'allure-js-commons'

const ANFORDERUNGEN: Record<string, string> = {
  '@anf-1': 'Anforderung 1 - Slot genau einmal buchbar',
  '@anf-2': 'Anforderung 2 - Slot in der Vergangenheit nicht buchbar',
  '@anf-3': 'Anforderung 3 - Stornierung bis 24 h vor Beginn',
  '@anf-4': 'Anforderung 4 - Stornierung gibt Slot frei',
  '@anf-5': 'Anforderung 5 - Keine erneute Stornierung',
  '@anf-6': 'Anforderung 6 - Unbekannte ID',
  '@anf-7': 'Anforderung 7 - Pflichtfelder und Laengen',
}
const OHNE_ANFORDERUNG = 'Frontend - keine nummerierte Anforderung'

export async function beschrifteFuerAllure(testInfo: TestInfo): Promise<void> {
  const anforderungen = testInfo.tags.filter((tag) => tag in ANFORDERUNGEN)
  for (const tag of anforderungen.length > 0 ? anforderungen : [null]) {
    await allure.epic(tag ? ANFORDERUNGEN[tag] : OHNE_ANFORDERUNG)
  }

  const istDefect = testInfo.tags.some((tag) => tag.startsWith('@Defect-'))
  await allure.feature(istDefect ? 'Defect-Nachweis (schlaegt bis zur Behebung fehl)' : 'Regression')

  const defect = testInfo.title.match(/Defect \d+/)?.[0]
  if (defect) {
    await allure.story(defect)
  }
}
