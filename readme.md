# JONATHAN HERRERA 00379823

## Indicaciones

Recientemente, se utilizó AI para crear un sistema de gestion de una biblioteca, el cual ha generado varios errores, su trabajo es arreglarlo. Dado el siguiente caso de uso, explique y/o resuelva cada problema según se le pida.

---

## Consideraciones

La libreria crea automaticamente un correo con los nombres de la persona

---

## Problemas

### 1. Filtro por autor y género (10%)

QA ha reportado que el endpoint para obtener los libros puede filtrar por **autor** y por **género**, o por cualquiera de los dos de manera individual.

Actualmente:

- Filtrar únicamente por autor funciona correctamente.
- Filtrar únicamente por género funciona correctamente.
- Filtrar por **autor y género al mismo tiempo** provoca que el servidor falle.

**Instrucción:** Explique la causa del problema y resuélvalo.

En BookService.getAllBooks() el filtro combinado invocaba bookRepository.findByAuthorAndGenre(genre, author) con los argumentos invertidos respecto a la firma del método findByAuthorAndGenre(String author, String genre).

Spring Data JPA construye la consulta a partir del nombre del método primero author, luego genre y de cierta forma ata los argumentos por posicion, como el campo genre de la entidad Book es un enum, Spring Data convierte automaticamente el segundo argumento recibido a Genre mediante Enum.valueOf(). Al estar invertidos los parametros, lo que en realidad se pasaba a esa posicion era el nombre del autor (ej "James Clear"), y al intentar convertirlo a Genre se lanzaba IllegalArgumentException no existe esa constante en el enum, lo que terminaba provocando el error 500.

practicamente corrigi el orden de los argumentos en la llamada: bookRepository.findByAuthorAndGenre(author, genre).



---

### 2. Error al volver a prestar un libro (10%)

Un usuario reportó que al pedir prestado el libro **The Selfish Gene**, devolverlo e intentar pedirlo prestado nuevamente, el servidor falla.

**Instrucción:** Explique la causa del problema y resuélvalo.

En MovementService.createMovement(), al prestar un libro (BORROWING) se decrementa availableCount y, si llega a 0, se marca available = false. Sin embargo, en la rama de devolucion (RETURN) solo se incrementaba availableCount, pero nunca se volvia a poner available = true.

"The Selfish Gene" tiene availableCount = 1 inicialmente al prestarlo: availableCount pasa a 0 y available queda en false. Al devolverlo: availableCount vuelve a 1, pero available sigue en false porque nadie lo restablece. Al intentar prestarlo de nuevo, la validacion if (!book.isAvailable()) sigue siendo verdadera y lanza RuntimeException("Book is not available"), resultando en el error 500 reportado.

la solución es en la rama de devolucion, si availableCount > 0 despues de incrementar, se restablece available = true.
---

### 3. Cantidad de libros por género (10%)

Existe un endpoint que devuelve la cantidad de libros disponibles por género. Sin embargo, actualmente dicho endpoint falla.

**Instrucción:** Explique la causa del problema y resuélvalo.

getGenresAvailable() recorre todos los libros (findAll()) y llama a book.getGenre().name() sin comprobar si el genero es null. El libro "The Art of War" tiene genre = NULL en data.sql, por lo que book.getGenre() retorna null y la llamada a .name() lanza un NullPointerException. Como ese registro siempre esta presente en la base de datos, el endpoint falla de forma consistente.

aquí agregué una validacion para omitir del conteo los libros cuyo genero sea null.



---

### 4. Error al consultar un libro por ID (10%)

Un miembro del equipo de frontend reporta que la siguiente llamada falla:

```http
GET /books?id=ed16ed1e-7017-4697-a08a-d28c09a74acf
```

**Instrucción:** Explique la causa del problema.

El controlador expone dos rutas distintas para libros: GET /books/{id} (el id viaja como variable de ruta) y GET /books (listado general, con filtros opcionales author y genre recibidos como query params via @RequestParam).

La peticion GET /books?id=ed16ed1e- no coincide con la ruta /books/{id}, sino con /books (sin id en la ruta). Como el metodo getAllBooks(author, genre) no declara ningun @RequestParam llamado id, Spring ignora ese parametro de query y no lanza error. El resultado es que la peticion no falla con un codigo de error HTTP, pero regresa el listado completo de libros en lugar del libro puntual esperado. El frontend, que espera recibir un unico objeto Book, "falla" al intentar interpretar un arreglo como si fuera un objeto individual.

La causa es un desacuerdo de contrato entre frontend y backend: el id del libro debe enviarse como parte de la ruta (GET /books/{id}), no como query param ?id=.


---

### 5. Error al crear un libro (10%)

QA ha reportado que el siguiente payload enviado al endpoint `POST /books` provoca un error:

```json
{
  "title": "Clean Code",
  "author": "Robert C. Martin",
  "genre": "classic",
  "isbn": "978-0132350884",
  "available": true,
  "availableCount": 5
}
```

**Instrucción:** Explique la causa del problema.
En BookService.createBook() el genero recibido en el DTO se convierte directamente con Genre.valueOf(dto.getGenre()), sin normalizar mayusculas/minusculas. El payload esta enviando "genre": "classic" en minusculas, pero las constantes del enum Genre estan definidas en mayusculas (CLASSIC, CRIME, etc.), y Enum.valueOf() es sensible a mayusculas/minusculas. Por lo tanto se lanza IllegalArgumentException: No enum constant Genre.classic, resultando en el error 500 reportado por QA.

updateBook() si normaliza el valor con .toUpperCase() antes de convertirlo (Genre.valueOf(dto.getGenre().toUpperCase())), por lo que el mismo payload en un PUT no fallaria. Esa inconsistencia entre createBook y updateBook es la causa raiz del comportamiento reportado: al metodo de creacion le falta la misma normalizacion que ya tiene el de actualizacion.

---

### 6. Devolución de libros no prestados (20%)

QA ha reportado que un usuario es capaz de devolver libros que nunca ha solicitado en préstamo.

**Instrucción:**

- Confirme si este comportamiento es realmente posible.
- Si es posible, explique la causa y resuelva el problema.
- Si no es posible, explique por qué, haciendo referencia al código correspondiente.

ERA posible ya que MovementService.returnBook() llama a createMovement(dto, RETURN), el cual unicamente valida que existan un Lector (por email) y un Book (por isbn), pero nunca verifica que ese lector tenga realmente un prestamo activo de ese libro especifico. Por lo tanto, cualquier lector registrado podia invocar POST /movements/return con el isbn de cualquier libro -incluso uno que jamas pidio prestado- y el sistema incrementaba availableCount igualmente y registraba un movimiento RETURN ficticio en el historial, inflando el inventario disponible por encima del stock real y corrompiendo la trazabilidad de movimientos.

---
