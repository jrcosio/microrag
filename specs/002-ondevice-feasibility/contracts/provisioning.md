# Procedimiento de aprovisionamiento de modelos (solo desarrollo) — F02

Los modelos **nunca** se versionan (`*.litertlm` está en `.gitignore`) y la app **no** los descarga en F02. La descarga en la app llega en F03.

## 1. Descarga en el ordenador

Origen: HF `litert-community`, con la URL fijada al commit de research R2.

```bash
MODELS_DIR="${MODELS_DIR:-$HOME/MicroRAG-models}"; mkdir -p "$MODELS_DIR"; cd "$MODELS_DIR"
dl() { curl -fL --retry 3 -C - -o "$2" "https://huggingface.co/$1/resolve/$3/$2"; }
dl litert-community/gemma-4-E2B-it-litert-lm              gemma-4-E2B-it.litertlm                    1caa527c1e88b55f046fcd8d2b4265606680663d
dl litert-community/gemma-4-E2B-it-litert-lm              gemma-4-E2B-it-gpu.litertlm                1caa527c1e88b55f046fcd8d2b4265606680663d
dl litert-community/embeddinggemma-2-740m-litert-lm       embeddinggemma-2-740m.litertlm             24d962e906c7d332c6428e71c9676855024569e2
dl litert-community/embeddinggemma-2-text-vision-440m-litert-lm embeddinggemma-2-text-vision-440m.litertlm e301f74d5551b0c2641bd5cb4652a76239d5c5f8
dl litert-community/embeddinggemma-2-text-270m-litert-lm  embeddinggemma-2-text-270m.litertlm        9be6e8b90982095dc05c2bd162e4b954ee4dbac7
shasum -a 256 *.litertlm      # comparar con research R2 / ModelCatalog
```

## 2. Copia al teléfono

Destino: la carpeta de desarrollo de la app. El `mkdir -p` crea la carpeta si la app aún no se ha abierto.

```bash
SERIAL=P31281001066   # Nothing Phone (1)
DEV=/sdcard/Android/data/com.lisdatasolutions.microrag/files/dev-models
adb -s $SERIAL shell mkdir -p $DEV
adb -s $SERIAL push "$MODELS_DIR"/*.litertlm $DEV/
```

## 3. Importación con verificación (en la app)

«MicroRAG Debug → Viabilidad on-device → Importar modelos de desarrollo»:
- copia cada fichero a `noBackupFilesDir/models/<fichero>.part` calculando su SHA-256 en streaming;
- si la huella coincide, renombra el `.part` al nombre final y borra la copia externa;
- si no coincide, borra el `.part` y deja la copia externa para poder inspeccionarla.

Si no coincide, el estado pasa a «Huella incorrecta» y el fichero no se carga.

## Plan B (si el shell no puede escribir en `Android/data`)

```bash
adb -s $SERIAL push "$MODELS_DIR"/<fichero> /data/local/tmp/
adb -s $SERIAL shell run-as com.lisdatasolutions.microrag sh -c 'mkdir -p no_backup/models && cp /data/local/tmp/<fichero> no_backup/models/'
adb -s $SERIAL shell rm /data/local/tmp/<fichero>
```

La app trata ese fichero como no verificado: «Verificar de nuevo» calcula su huella antes de permitir cargarlo, y si no coincide lo borra.

**Espacio necesario:** ≈ 5,6 GB en el ordenador. En el teléfono, ≈ 5,6 GB más el pico de la copia de un fichero (≈ 2,6 GB) durante la importación.
