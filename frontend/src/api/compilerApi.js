const headers = {
  'Content-Type': 'application/json'
}

async function post(path, body) {
  const response = await fetch(path, {
    method: 'POST',
    headers,
    body: JSON.stringify(body)
  })
  const payload = await response.json()
  if (!response.ok || payload.success === false) {
    return { ok: false, payload }
  }
  return { ok: true, payload }
}

export function lex(code) {
  return post('/api/lexer', { code })
}

export function parse(code) {
  return post('/api/parser', { code })
}

export function compile(code) {
  return post('/api/compile', { code })
}

export function run(code, stdin) {
  return post('/api/run', { code, stdin })
}
