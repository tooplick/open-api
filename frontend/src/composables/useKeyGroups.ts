import { computed, ref } from 'vue'
import { getPublicSettings } from '@/api/setting'

const FALLBACK_GROUP = 'default'

function parseGroups(raw?: string | null): string[] {
  const groups: string[] = []
  const seen = new Set<string>()
  for (const part of (raw ?? '').split(',')) {
    const group = part.trim()
    if (group && !seen.has(group)) {
      groups.push(group)
      seen.add(group)
    }
  }
  return groups.length > 0 ? groups : [FALLBACK_GROUP]
}

function resolveDefaultGroup(rawDefault: string | null | undefined, groups: string[]): string {
  const defaultGroup = rawDefault?.trim()
  return defaultGroup && groups.includes(defaultGroup) ? defaultGroup : groups[0]
}

export function useKeyGroups() {
  const defaultGroup = ref(FALLBACK_GROUP)
  const keyGroups = ref(FALLBACK_GROUP)
  const groupList = computed(() => parseGroups(keyGroups.value))

  async function loadKeyGroups(): Promise<void> {
    try {
      const settings = await getPublicSettings()
      const groups = parseGroups(settings.keyGroups)
      keyGroups.value = groups.join(',')
      defaultGroup.value = resolveDefaultGroup(settings.defaultKeyGroup, groups)
    } catch {
      keyGroups.value = FALLBACK_GROUP
      defaultGroup.value = FALLBACK_GROUP
    }
  }

  return {
    defaultGroup,
    groupList,
    loadKeyGroups,
  }
}
