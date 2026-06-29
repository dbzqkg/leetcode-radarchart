export async function onRequest() {
  return new Response(
    JSON.stringify({
      updatetime: new Date().toISOString().slice(0, 10),
      usageTotal: 0,
      usageToday: 0,
      pageTotal: 0,
      pageToday: 0,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
}
