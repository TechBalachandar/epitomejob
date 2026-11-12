import next from "eslint-config-next";

const config = [
  { ignores: ["public/mockServiceWorker.js"] },
  ...next,
];

export default config;
