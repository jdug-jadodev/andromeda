# Copia vendorizada del contrato

Esta carpeta es una copia de **andromeda-contrato v1.0.0**.
No se edita a mano.

- `home-api-v1/` contiene la API, las políticas, los límites y los errores.
- `VERSION` es la versión del contrato.
- `SHA256SUMS` permite detectar cualquier edición accidental.

Verificar:

```powershell
python tools\verify_contract.py
```

Los tests de Python y Java comparan sus constantes con `policies.json` y
`limits.json`. Si un lado cambia sin el otro, el test falla.
