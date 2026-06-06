const fs = require('fs');
const glob = require('glob');
const zlib = require('zlib');

(async () => {

/** @type {String[]} */
const files = await glob.glob('dist/**/*.{js,css,html,svg}');

await Promise.all(files.map(e => [
    new Promise((resolve, reject) => {
        const readStream = fs.createReadStream(e);
        const writeStream = fs.createWriteStream(e + '.gz');
        readStream
            .pipe(zlib.createGzip({ level: 9 }))
            .pipe(writeStream)
            .on('finish', error => error ? reject(error) : resolve());
    }),
    new Promise((resolve, reject) => {
        const readStream = fs.createReadStream(e);
        const writeStream = fs.createWriteStream(e + '.br');
        readStream
            .pipe(zlib.createBrotliCompress({ params: { [zlib.constants.BROTLI_PARAM_QUALITY]: zlib.constants.BROTLI_MAX_QUALITY }}))
            .pipe(writeStream)
            .on('finish', error => error ? reject(error) : resolve());
    }),
]).flat());

})()
