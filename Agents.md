## Proyecto: app de gastos compartidos

## Stack
- Kotlin 2.1 / Jetpack Compose BOM 2026.03.00
- Architecture: MVVM + Clean Architecture
- Backend: Supabase (auth + PostgreSQL + Realtime)
- DI: Hilt
- Navigation: Compose Navigation + Deep Links
- Theme: Material Design 3

## Contexto
App multiusuario de gastos compartidos con sincronización
en tiempo real. Cualquier cambio de un usuario se refleja
instantáneamente en los demás. Sin Room. Supabase es la
única fuente de verdad. Auth manejada por Supabase.
Los usuarios se invitan a grupos via deep link compartido
por WhatsApp o cualquier app. El flujo de incorporación
es completamente automático para el invitado.

## Patrones y buenas prácticas
- UDF: UI emite Events, ViewModel procesa, State fluye hacia UI
- Todos los llamados a Supabase retornan Result<T>
  (Success, Error, Loading)
- Un ViewModel por pantalla
- Cero lógica de negocio en Composables
- Cálculo de saldos siempre en Repository, nunca en UI
- Coroutines + Flow para async, nunca callbacks
- Constantes centralizadas en AppConstants
- Hilt para toda la inyección de dependencias

## Convenciones
- Sealed classes para estados UI
- StateFlow en todos los ViewModels
- Un archivo por Composable
- Nunca strings como IDs de usuario, siempre UUID de Supabase
- Debounce 300ms en todos los campos de búsqueda
- Tokens de invitación expiran a las 72 horas

## Tablas Supabase
- profiles (id, email, display_name, avatar_url, fcm_token, created_at)
- groups (id, name, created_by, created_at)
- group_members (group_id, user_id)
- group_invites (id, group_id, created_by, token, expires_at, used)
- expenses (id, group_id, paid_by, amount, description, date)
- expense_splits (expense_id, user_id, amount)
- payments (id, group_id, from_user, to_user, amount, date)