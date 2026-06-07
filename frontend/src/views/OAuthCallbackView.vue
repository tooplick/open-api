<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { useAuthStore } from '@/stores/auth'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

function firstQueryValue(value: unknown): string {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }
  return typeof value === 'string' ? value : ''
}

function safeRedirect(value: string): string {
  if (!value || !value.startsWith('/') || value.startsWith('//')) {
    return '/dashboard'
  }
  if (value.startsWith('/api/') || value.startsWith('/v1/') || value.startsWith('/anthropic/')) {
    return '/dashboard'
  }
  return value
}

onMounted(async () => {
  const error = firstQueryValue(route.query.error)
  const redirect = safeRedirect(firstQueryValue(route.query.redirect))
  if (error) {
    toast.error(error)
    await router.replace({ name: 'login', query: { redirect } })
    return
  }

  const ticket = firstQueryValue(route.query.ticket)
  if (!ticket) {
    toast.error('GitHub OAuth login failed')
    await router.replace({ name: 'login', query: { redirect } })
    return
  }

  try {
    await auth.exchangeGithubTicket(ticket)
    await router.replace(redirect)
  }
  catch {
    await router.replace({ name: 'login', query: { redirect } })
  }
})
</script>

<template>
  <div class="app-auth-bg flex min-h-screen items-center justify-center p-6">
    <Card class="w-full max-w-[360px] shadow-lg">
      <CardHeader>
        <CardTitle class="text-base">
          GitHub
        </CardTitle>
      </CardHeader>
      <CardContent class="text-muted-foreground flex items-center gap-2 text-sm">
        <Spinner />
        <span>Signing in...</span>
      </CardContent>
    </Card>
  </div>
</template>
