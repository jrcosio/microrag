# Formato del informe de viabilidad — F02

**Ubicación en el dispositivo:** `filesDir/reports/feasibility-<yyyyMMdd-HHmmss>.{json,md}`.

**Recuperación** (app debuggable):

```bash
adb -s <serial> exec-out run-as com.lisdatasolutions.microrag ls files/reports
adb -s <serial> exec-out run-as com.lisdatasolutions.microrag cat files/reports/<fichero>.md > benchmarks.md
```

## JSON (`schemaVersion = 1`)

```json
{
  "schemaVersion": 1,
  "createdAt": "2026-10-25T23:10:00+01:00",
  "device": {
    "manufacturer": "Nothing", "model": "A063", "socManufacturer": "QTI", "socModel": "SM7325",
    "androidRelease": "15", "sdkInt": 35, "totalRamMb": 11236,
    "thermalStatus": "NONE", "thermalHeadroom": 0.42, "charging": true, "batteryLevel": 80, "batteryTempC": 31.0,
    "appVersion": "0.1.0", "liteRtLmVersion": "0.18.1"
  },
  "models": [
    { "id": "EG2_270M", "fileName": "embeddinggemma-2-text-270m.litertlm", "sha256": "2d079e…86fb", "revision": "9be6e8…dbac7", "state": "VERIFIED" }
  ],
  "results": [
    {
      "kind": "EMBEDDING", "model": "EG2_270M", "backend": "CPU", "status": "COMPLETED",
      "protocol": { "warmup": 3, "measured": 30 },
      "thermalStart": "NONE", "thermalEnd": "LIGHT", "charging": true, "peakRssMb": 412, "peakRssResetSupported": true,
      "metrics": {
        "initColdMs": 1830, "initWarmMs": 610, "queryP50Ms": 38.2, "queryP95Ms": 44.9,
        "documentP50Ms": 96.0, "documentP95Ms": 104.3, "rawDims": 768, "outputDims": 256, "l2Norm": 1.0000001,
        "declaredMaxContextTokens": 8192, "supportedBackends": ["CPU", "GPU"]
      }
    }
  ],
  "exitCriteria": { "ttftMeasured": true, "bestQueryP95CpuMs": 44.9, "queryUnder150msCpu": true }
}
```

Las cifras del ejemplo son **ilustrativas** (constitución V). El informe real solo contiene medidas.

Valores posibles de `status`:
- `COMPLETED`;
- `CANCELLED`, con `atPhase`;
- `FAILED`, con `reason`;
- `NOT_AVAILABLE`, con `reason`.

## Markdown

1. **Cabecera:** fecha, dispositivo, versiones y estado térmico inicial.
2. **Modelos:** tabla con id, fichero, MB, huella abreviada, revisión abreviada y estado.
3. **Embeddings:** tabla por variante × backend con inicialización en frío y con caché, consulta p50/p95, documento p50/p95, dimensiones (bruta → final), norma, contexto máximo, memoria pico, estado térmico inicial y final, y estado.
4. **Compatibilidad CPU↔GPU:** variante, número de textos, coseno mínimo y medio, compatible sí/no.
5. **Generación:** tabla por fichero × backend con inicialización en frío y con caché, tokens del prompt, TTFT (benchmark y reloj de pared), prefill y decode en tok/s, memoria pico, estado térmico inicial y final, plan B y estado.
6. **Criterios de salida de la fase 0:** TTFT medido, mejor p95 de consulta en CPU y si cumple < 150 ms.
7. **Muestra de salida:** primeras ~400 palabras generadas por cada fichero, para comprobar que la respuesta es coherente.
