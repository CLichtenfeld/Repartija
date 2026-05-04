import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { create, getNumericDate } from "https://deno.land/x/djwt@v3.0.1/mod.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const { groupId, transactionId, transactionType, changedBy, affectedUserIds } = await req.json()

    // 1. Fetch info for the notification
    const { data: group } = await supabaseClient.from('groups').select('name').eq('id', groupId).single()
    const { data: changer } = await supabaseClient.from('profiles').select('display_name').eq('id', changedBy).single()
    
    let description = ""
    let amount = 0

    if (transactionType === 'expense') {
      const { data: expense } = await supabaseClient.from('expenses').select('description, amount').eq('id', transactionId).single()
      description = expense?.description || "un gasto"
      amount = expense?.amount || 0
    } else {
      const { data: payment } = await supabaseClient.from('payments').select('amount').eq('id', transactionId).single()
      description = "un pago"
      amount = payment?.amount || 0
    }

    // 2. Fetch FCM tokens
    const { data: profiles } = await supabaseClient
      .from('profiles')
      .select('fcm_token')
      .in('id', affectedUserIds)
      .neq('id', changedBy)
      .not('fcm_token', 'is', null)

    const tokens = profiles?.map(p => p.fcm_token) || []
    if (tokens.length === 0) {
      return new Response(JSON.stringify({ message: 'No tokens found' }), { 
        headers: { ...corsHeaders, 'Content-Type': 'application/json' } 
      })
    }

    // 3. Generate FCM Access Token
    const serviceAccount = JSON.parse(Deno.env.get('FIREBASE_SERVICE_ACCOUNT') || '{}')
    const accessToken = await getAccessToken(serviceAccount)

    // 4. Send notifications
    const notificationPromises = tokens.map(token => {
      return fetch(`https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`
        },
        body: JSON.stringify({
          message: {
            token: token,
            notification: {
              title: `Movimiento en ${group?.name || 'Repartija'}`,
              body: `${changer?.display_name || 'Alguien'} modificó ${description} por $${amount.toFixed(2)}`
            },
            data: {
              groupId,
              transactionId,
              transactionType
            }
          }
        })
      })
    })

    await Promise.all(notificationPromises)

    return new Response(JSON.stringify({ success: true }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })

  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})

async function getAccessToken(serviceAccount: any) {
  const pemHeader = "-----BEGIN PRIVATE KEY-----";
  const pemFooter = "-----END PRIVATE KEY-----";
  const pemContents = serviceAccount.private_key
    .replace(pemHeader, "")
    .replace(pemFooter, "")
    .replace(/\s/g, "");
  
  const binaryDerString = atob(pemContents);
  const binaryDer = new Uint8Array(binaryDerString.length);
  for (let i = 0; i < binaryDerString.length; i++) {
    binaryDer[i] = binaryDerString.charCodeAt(i);
  }

  const key = await crypto.subtle.importKey(
    "pkcs8",
    binaryDer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const jwt = await create(
    { alg: "RS256", typ: "JWT" },
    {
      iss: serviceAccount.client_email,
      sub: serviceAccount.client_email,
      aud: "https://oauth2.googleapis.com/token",
      iat: getNumericDate(0),
      exp: getNumericDate(3600),
      scope: "https://www.googleapis.com/auth/firebase.messaging"
    },
    key
  );

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });

  const { access_token } = await res.json();
  return access_token;
}
