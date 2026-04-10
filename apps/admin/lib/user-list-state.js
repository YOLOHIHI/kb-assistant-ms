export function mergeUsersByPreviousOrder(previousUsers, nextUsers) {
  const previous = Array.isArray(previousUsers) ? previousUsers : []
  const incoming = Array.isArray(nextUsers) ? nextUsers : []
  const incomingById = new Map(
    incoming
      .filter((user) => user?.id)
      .map((user) => [String(user.id), user])
  )

  const ordered = []
  for (const user of previous) {
    const id = String(user?.id || "")
    if (!id || !incomingById.has(id)) continue
    ordered.push(incomingById.get(id))
    incomingById.delete(id)
  }

  for (const user of incoming) {
    const id = String(user?.id || "")
    if (!id || !incomingById.has(id)) continue
    ordered.push(incomingById.get(id))
    incomingById.delete(id)
  }

  return ordered
}

export function upsertUserRow(users, nextUser) {
  const current = Array.isArray(users) ? users : []
  const nextId = String(nextUser?.id || "")
  if (!nextId) return current

  let matched = false
  const updated = current.map((user) => {
    if (String(user?.id || "") !== nextId) return user
    matched = true
    return nextUser
  })

  return matched ? updated : [...updated, nextUser]
}
