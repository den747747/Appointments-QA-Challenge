import { expect, type APIRequestContext } from '@playwright/test'

export type Slot = { id: string; arzt: string; beginn: string }
export type Termin = { id: string; slotId: string; patientenname: string; status: string }

/** Eindeutiger Suffix, damit parallel oder wiederholt laufende Tests sich nicht in die Quere kommen. */
export function eindeutig(praefix: string): string {
  return `${praefix} ${Date.now()}-${Math.floor(Math.random() * 1e6)}`
}

function lokaleZeit(datum: Date): string {
  const pad = (wert: number) => String(wert).padStart(2, '0')
  return (
    `${datum.getFullYear()}-${pad(datum.getMonth() + 1)}-${pad(datum.getDate())}` +
    `T${pad(datum.getHours())}:${pad(datum.getMinutes())}:${pad(datum.getSeconds())}`
  )
}

export async function neuerSlot(
  request: APIRequestContext,
  stundenInDerZukunft: number,
  arzt = 'Dr. Testlauf',
): Promise<Slot> {
  const beginn = new Date(Date.now() + stundenInDerZukunft * 60 * 60 * 1000)
  const antwort = await request.post('/api/slots', { data: { arzt, beginn: lokaleZeit(beginn) } })
  expect(antwort.status()).toBe(201)
  return (await antwort.json()) as Slot
}

/** Test Data vorbereitung */
export async function bucheUeberApi(
  request: APIRequestContext,
  slotId: string,
  patientenname: string,
  grund = 'Kontrolle',
): Promise<Termin> {
  const antwort = await request.post('/api/appointments', { data: { slotId, patientenname, grund } })
  expect(antwort.status()).toBe(201)
  return (await antwort.json()) as Termin
}
