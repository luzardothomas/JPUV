# JPUV - Just Play Your Videos

> Es un reproductor de video con VLC integrado, es compatible con Android 11 en adelante, su función principal es importar enlaces a videos locales de un dispositivo o de un servidor local o externo. Tiene funcionalidades semejantes a las plataformas de Streaming como poder saltar la intro y poder seguir series por sus temporadas configurando sus tracks de audio y subtítulos.

<div align="center">
  <img src="https://github.com/user-attachments/assets/9028c200-5c90-43fb-baa6-0abf916e40c3" width="675" alt="logo_with_name">
</div>

## Apariencia de la Aplicación
### Versión de TV
<details>
  <summary>Ver imágenes</summary>
<img width="1509" height="849" alt="Screenshot from 2026-03-20 15-35-37" src="https://github.com/user-attachments/assets/753fe1fd-3209-4dc4-b526-e6a8a36d7644" />
<img width="1509" height="849" alt="Screenshot from 2026-03-20 15-36-02" src="https://github.com/user-attachments/assets/c58b9f94-407b-459b-b6e3-0142284b6ac4" />
<img width="1509" height="849" alt="Screenshot from 2026-03-20 15-36-12" src="https://github.com/user-attachments/assets/11c7dee4-830d-4a85-8b6d-2af694f9fd58" />
</details>

### Versión de Mobile
<details>
  <summary>Ver imágenes</summary>
<img width="1622" height="718" alt="Screenshot from 2026-03-20 15-40-09" src="https://github.com/user-attachments/assets/87e9044d-cb36-47c9-a560-dfe3bb3005ac" />
<img width="1622" height="718" alt="Screenshot from 2026-03-20 15-40-38" src="https://github.com/user-attachments/assets/b7f3f968-55eb-4df8-a439-329b2b9c0538" />
<img width="1622" height="718" alt="Screenshot from 2026-03-20 15-40-57" src="https://github.com/user-attachments/assets/1dce23eb-740a-4a95-af3a-5ca46ef0caa6" />
</details>

## Aplicación y Guía de usuario

<p align="center">
  <a href="https://github.com/luzardothomas/JPUV/releases/download/v1.0.0/jpuv_v1.0.0.apk">
    <img src="https://img.shields.io/badge/Descargar-APK-brightgreen?style=for-the-badge&logo=android" alt="Descargar APK">
  </a>
  <a href="https://github.com/luzardothomas/JPUV/releases/download/v1.0.0/jpuv_guia_de_usuario_v1.0.0.pdf">
    <img src="https://img.shields.io/badge/Descargar-PDF-red?style=for-the-badge&logo=adobeacrobatreader&logoColor=white" alt="Descargar PDF">
  </a>
</p>

## Tecnologías utilizadas

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/VLC-FF8800?style=for-the-badge&logo=videolan&logoColor=white" alt="VLC" />
  <img src="https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black" alt="JavaScript" />
  <img src="https://img.shields.io/badge/Cloudflare-F38020?style=for-the-badge&logo=cloudflare&logoColor=white" alt="Cloudflare" />
</p>

## Usuario para el que está enfocado

La aplicación está principalmente enfocada a aquellos usuarios que tengan una buena colección de películas o series a su disposición. No requiere conocimientos informáticos previos siempre y cuando tenga un disco duro externo o pendrive con muchos gigas. Con este medio de almacenamiento solamente van a tener que darle permisos a la app para el almacenamiento y luego darle clic a “Importar de dispositivo”, y prácticamente sería un Plug & Play (conéctalo y usalo).
Igualmente todos pueden usar la aplicación si se la usa con el contexto apropiado. El que se encargue de configurar el servidor puede darle acceso a su familiar o amigo cercano, una analogía válida sería el que crea un grupo de WhatsApp y agrega a su familia para poder compartir mensajes juntos, esto sería lo mismo pero con su colección de películas y series.

## Contextos de uso
### Primer contexto
Hay tres formas distintas de importar videos, la primera es mediante un medio de almacenamiento externo que se conecte al dispositivo, esto es reproducción local (ergo no necesitas de internet para reproducirlo).
### Segundo contexto
La segunda forma es con un servidor local, en concreto tiene que ser el SMB de Samba (Linux), no confundir con el SMB nativo que tiene Windows porque este pide muchos permisos y dificulta mucho la transmisión de datos de la aplicación. En este segundo contexto se requiere de una conexión a internet, el servidor local va a estar alojado en la computadora que vamos a usar como medio de almacenamiento.
### Tercer contexto
Si queremos un servidor externo el mismo va a necesitar un SMB (lectura de los archivos) de los medios para los metadatos y un WebDAV (reproducción de los videos) para la transmisión de datos. Una vez que configuremos esto vamos a necesitar un puente para poder conectarlos al servidor. Al igual que en el segundo contexto, acá vamos a requerir que una computadora quede prendida para poder acceder al servidor. 
