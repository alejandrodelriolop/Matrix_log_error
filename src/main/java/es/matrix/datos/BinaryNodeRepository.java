package es.matrix.datos;

import es.matrix.modelo.NodoRegistro;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/** Acceso directo a nodos.dat. Un registro ocupa exactamente 28 bytes. */
public class BinaryNodeRepository {
    private final Path archivo;

    public BinaryNodeRepository(Path archivo) throws IOException {
        this.archivo = archivo;
        Path padre = archivo.toAbsolutePath().getParent();
        if (padre != null) Files.createDirectories(padre);
        if (Files.notExists(archivo)) Files.createFile(archivo);
        validarLongitud();
    }

    public void anadir(NodoRegistro nodo) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(archivo.toFile(), "rw")) {
            validarLongitud(raf.length());
            long cantidad = raf.length() / NodoRegistro.TAMANO_REGISTRO;
            if (nodo.idNumerico() != cantidad + 1) {
                throw new IOException("Para mantener el acceso directo, el ID nuevo debe ser " + (cantidad + 1));
            }
            raf.seek(raf.length());
            escribirRegistro(raf, nodo);
        }
    }

    /** Lee un nodo directamente: no recorre los registros anteriores. */
    public NodoRegistro leerPorId(int idNumerico) throws IOException {
        if (idNumerico < 1) throw new IllegalArgumentException("El ID debe ser mayor que cero.");
        try (RandomAccessFile raf = new RandomAccessFile(archivo.toFile(), "r")) {
            validarLongitud(raf.length());
            long posicion = (long) (idNumerico - 1) * NodoRegistro.TAMANO_REGISTRO;
            if (posicion + NodoRegistro.TAMANO_REGISTRO > raf.length()) {
                throw new IOException("No existe el nodo con ID " + idNumerico);
            }
            raf.seek(posicion);
            NodoRegistro nodo = leerRegistro(raf);
            if (nodo.idNumerico() != idNumerico) {
                throw new IOException("El ID almacenado no coincide con la posición esperada.");
            }
            return nodo;
        }
    }

    /** Modifica el registro en su posición; no reescribe el fichero completo. */
    public void modificar(NodoRegistro nodo) throws IOException {
        if (nodo.idNumerico() < 1) throw new IllegalArgumentException("El ID debe ser mayor que cero.");
        try (RandomAccessFile raf = new RandomAccessFile(archivo.toFile(), "rw")) {
            validarLongitud(raf.length());
            long posicion = (long) (nodo.idNumerico() - 1) * NodoRegistro.TAMANO_REGISTRO;
            if (posicion + NodoRegistro.TAMANO_REGISTRO > raf.length()) {
                throw new IOException("No existe el nodo con ID " + nodo.idNumerico());
            }
            raf.seek(posicion);
            int idGuardado = raf.readInt();
            if (idGuardado != nodo.idNumerico()) {
                throw new IOException("El ID almacenado no coincide con la posición esperada.");
            }
            raf.seek(posicion);
            escribirRegistro(raf, nodo);
        }
    }

    public long contarRegistros() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(archivo.toFile(), "r")) {
            validarLongitud(raf.length());
            return raf.length() / NodoRegistro.TAMANO_REGISTRO;
        }
    }

    private void validarLongitud() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(archivo.toFile(), "r")) {
            validarLongitud(raf.length());
        }
    }

    private static void validarLongitud(long longitud) throws IOException {
        if (longitud % NodoRegistro.TAMANO_REGISTRO != 0) {
            throw new IOException("nodos.dat está incompleto o corrupto: su tamaño no es múltiplo de 28 bytes.");
        }
    }

    private static void escribirRegistro(RandomAccessFile raf, NodoRegistro nodo) throws IOException {
        raf.writeInt(nodo.idNumerico());
        raf.writeInt(nodo.nivelSincronizacion());
        raf.writeDouble(nodo.carga());
        raf.writeInt(nodo.codigoEstado());
        raf.writeLong(nodo.numeroOperaciones());
    }

    private static NodoRegistro leerRegistro(RandomAccessFile raf) throws IOException {
        try {
            return new NodoRegistro(raf.readInt(), raf.readInt(), raf.readDouble(), raf.readInt(), raf.readLong());
        } catch (EOFException e) {
            throw new IOException("Registro binario incompleto.", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("Registro binario con valores no válidos.", e);
        }
    }
}