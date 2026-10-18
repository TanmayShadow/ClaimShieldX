import { setupServer } from 'msw/node'

/** Network-level test double. Any request a test did not expect fails loudly (see setup.ts). */
export const server = setupServer()
