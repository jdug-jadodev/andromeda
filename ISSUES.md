# Errores y requisitos pendientes

Registro manual (sin modificar código). Pendiente de resolver.

## Bug

1. **No funciona crear recordatorios/alarmas desde la app.**
   - Al pedir un recordatorio sin hora («recuérdame comprar pan»), la casa abría un diálogo de varios pasos que se quedaba pendiente y se tragaba la siguiente orden (la app dejaba de responder y el Moto no despertaba).
   - Mitigación aplicada: ahora responde una pregunta directa sin abrir diálogo, pero **no se completa la creación del recordatorio** (falta capturar la fecha/hora y crearlo de verdad desde la app).

## Requisitos

1. **La app debe permitir ver, y editar, alarmas, recordatorios, temporizadores, etc.**
   - Listar alarmas, recordatorios y temporizadores guardados.
   - Editarlos (cambiar hora/texto) y eliminarlos desde la app.
   - Hoy la app solo envía órdenes; no hay pantalla para consultar ni editar la agenda.
