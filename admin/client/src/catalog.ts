import { api } from './api';
let catalog: Promise<any>;
export const getCatalog = () =>
  (catalog ||= api('/api/items/catalog').catch((e) => {
    catalog = undefined;
    throw e;
  }));
