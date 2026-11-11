import { get, post, put, api } from './api.js';
import { h, toast, money, dt, pill, field, input, select, formDialog, confirmDialog, table, alertBox, describe } from './ui.js';

const UNITS = ['PCS', 'KG', 'GRAM', 'LITER', 'ML', 'BOX', 'PACK', 'SACK', 'DOZEN', 'PAIR', 'SET', 'BOTTLE', 'BAG', 'METER', 'ROLL'];
const BIG = '?size=200&sort=name,asc';

const header = (title, sub, ...actions) =>
  h('div', { class: 'page-head' }, h('div', {}, h('h1', { text: title }), sub ? h('p', { class: 'muted', text: sub }) : null), h('div', { class: 'row-actions' }, actions));

/** Re-render helper: views are functions (ctx) => Promise<Node>; `reload` re-runs the current one. */

// ------------------------------------------------------------------ dashboard
export async function dashboard() {
  const [products, low, tx] = await Promise.all([
    get('/api/products' + BIG), get('/api/products/low-stock'), get('/api/transactions?size=8&sort=createdAt,desc'),
  ]);
  const active = products.content.filter((p) => p.active);
  const stat = (n, l, warn) => h('div', { class: 'stat' + (warn ? ' warn' : '') }, h('span', { class: 'n', text: n }), h('span', { class: 'l', text: l }));
  return h('div', {},
    header('Today', 'Where stock stands right now.'),
    h('div', { class: 'stats' },
      stat(products.totalElements, 'Products'),
      stat(active.length, 'Active'),
      stat(low.length, 'Low on stock', low.length > 0)),
    low.length ? h('div', { class: 'card flush' },
      h('h2', { class: 'card-title', text: 'Reorder soon' }),
      table([
        { label: 'Product', render: (p) => p.name },
        { label: 'In stock', num: true, render: (p) => p.quantityInStock + ' ' + (p.unit || '') },
        { label: 'Minimum', num: true, render: (p) => p.minStockLevel },
        { label: 'Supplier', render: (p) => p.supplierName || '-' },
      ], low)) : alertBox('Nothing is below its minimum level.', 'info'),
    h('h2', { text: 'Latest movements' }),
    h('div', { class: 'card flush' }, movementsTable(tx.content)));
}

function movementsTable(rows) {
  return table([
    { label: 'When', render: (t) => dt(t.transactionDate || t.createdAt) },
    { label: 'Product', render: (t) => t.productName },
    { label: 'Type', render: (t) => pill(t.transactionType.replace('_', ' '), t.transactionType === 'STOCK_OUT' ? 'warn' : t.transactionType === 'ADJUSTMENT' ? 'off' : '') },
    { label: 'Qty', num: true, render: (t) => t.quantity },
    { label: 'By', render: (t) => t.performedByUsername || '-' },
    { label: 'Note', render: (t) => t.notes || t.referenceNo || '' },
  ], rows, 'No stock movements yet.');
}

// ------------------------------------------------------------------- products
export async function products(ctx) {
  const [page, cats, sups] = await Promise.all([get('/api/products' + BIG), get('/api/categories' + BIG), get('/api/suppliers' + BIG)]);
  const isAdmin = ctx.user.role === 'ADMIN';
  const q = h('input', { type: 'search', placeholder: 'Search name, SKU or brand', 'aria-label': 'Search products' });
  const showInactive = h('input', { type: 'checkbox' });
  const holder = h('div', { class: 'card flush' });

  const draw = () => {
    const term = q.value.trim().toLowerCase();
    const rows = page.content.filter((p) => (showInactive.checked || p.active)
      && (!term || [p.name, p.sku, p.brand].some((v) => v && v.toLowerCase().includes(term))));
    holder.replaceChildren(table([
      { label: 'Product', render: (p) => h('div', {}, h('strong', { text: p.name }), h('div', { class: 'muted mono', text: p.sku + (p.brand ? ' · ' + p.brand : '') })) },
      { label: 'Category', render: (p) => p.categoryName || '-' },
      { label: 'In stock', num: true, render: (p) => h('span', {}, p.quantityInStock + ' ' + (p.unit || ''), ' ', p.lowStock ? pill('Low', 'warn') : null) },
      { label: 'Price', num: true, render: (p) => money(p.unitPrice) },
      { label: 'Status', render: (p) => (p.active ? pill('Active') : pill('Inactive', 'off')) },
      isAdmin ? { label: '', actions: true, render: (p) => [
        h('button', { class: 'small', text: 'Edit', onclick: () => productForm(ctx, p, cats.content, sups.content) }),
        h('button', { class: 'small ' + (p.active ? 'danger' : ''), text: p.active ? 'Deactivate' : 'Activate', onclick: () => toggleActive(ctx, p) }),
      ] } : null,
    ].filter(Boolean), rows, 'No products match.'));
  };
  q.addEventListener('input', draw);
  showInactive.addEventListener('change', draw);
  draw();

  return h('div', {},
    header('Products', page.totalElements + ' in the catalogue',
      isAdmin ? h('button', { class: 'primary', text: 'Add product', onclick: () => productForm(ctx, null, cats.content, sups.content) }) : null),
    h('div', { class: 'toolbar' }, q, h('label', { class: 'check' }, showInactive, 'Show inactive')),
    holder);
}

async function toggleActive(ctx, p) {
  try {
    await put(`/api/products/${p.id}/${p.active ? 'deactivate' : 'activate'}`);
    toast(p.active ? 'Product deactivated' : 'Product activated');
    ctx.reload();
  } catch (e) { toast(describe(e), true); }
}

function productForm(ctx, p, cats, sups) {
  const v = p || {};
  const f = (name, o) => input(name, { value: v[name], ...o });
  const catSel = select('categoryId', [['', '- none -'], ...cats.map((c) => [c.id, c.name])], cats.find((c) => c.name === v.categoryName)?.id);
  const supSel = select('supplierId', [['', '- none -'], ...sups.map((s) => [s.id, s.name])], sups.find((s) => s.name === v.supplierName)?.id);
  const unitSel = select('unit', UNITS.map((u) => [u, u]), v.unit || 'PCS');
  const expiry = h('input', { type: 'checkbox', name: 'expiryTracked', checked: !!v.expiryTracked });
  formDialog({
    title: p ? 'Edit product' : 'New product',
    fields: [
      field('Name', f('name', { required: true, maxlength: 150 })),
      h('div', { class: 'grid2' }, field('SKU', f('sku', { required: true, maxlength: 100 })), field('Brand', f('brand', { maxlength: 100 }))),
      h('div', { class: 'grid2' }, field('Category', catSel), field('Supplier', supSel)),
      h('div', { class: 'grid2' }, field('Selling price (Rp)', f('unitPrice', { required: true, type: 'number', min: '0.01', step: '0.01', inputmode: 'decimal' })),
        field('Cost price (Rp)', f('costPrice', { type: 'number', min: '0', step: '0.01', inputmode: 'decimal' }))),
      h('div', { class: 'grid2' }, field('Unit', unitSel),
        field('Minimum stock', f('minStockLevel', { required: true, type: 'number', min: '0', step: '1', value: v.minStockLevel ?? 0 }))),
      p ? null : field('Opening stock', f('quantityInStock', { required: true, type: 'number', min: '0', step: '1', value: 0 }),
        'Later changes go through Stock movements so they stay on record.'),
      field('Barcode', f('barcode', { maxlength: 100 })),
      h('label', { class: 'check' }, expiry, 'Track expiry dates (required for jams)'),
    ],
    onSubmit: async (val) => {
      const body = {
        name: val.name.trim(), sku: val.sku.trim(), brand: val.brand.trim() || null, barcode: val.barcode.trim() || null,
        description: v.description || null,
        unitPrice: Number(val.unitPrice), costPrice: val.costPrice === '' ? null : Number(val.costPrice),
        minStockLevel: Number(val.minStockLevel), quantityInStock: p ? p.quantityInStock : Number(val.quantityInStock),
        unit: val.unit, expiryTracked: val.expiryTracked,
        categoryId: val.categoryId ? Number(val.categoryId) : null, supplierId: val.supplierId ? Number(val.supplierId) : null,
      };
      await (p ? put(`/api/products/${p.id}`, body) : post('/api/products', body));
      toast(p ? 'Product updated' : 'Product added');
      ctx.reload();
    },
  });
}

// ---------------------------------------------------------------------- stock
export async function stock(ctx) {
  const [prods, tx] = await Promise.all([get('/api/products' + BIG), get('/api/transactions?size=50&sort=createdAt,desc')]);
  const active = prods.content.filter((p) => p.active);
  const record = (type) => () => stockForm(ctx, type, active);
  return h('div', {},
    header('Stock movements', 'Everything in and out, with who did it.',
      h('button', { class: 'primary', text: 'Stock in', onclick: record('STOCK_IN') }),
      h('button', { text: 'Stock out', onclick: record('STOCK_OUT') }),
      ctx.user.role === 'ADMIN' ? h('button', { text: 'Adjust', onclick: record('ADJUSTMENT') }) : null),
    h('div', { class: 'card flush' }, movementsTable(tx.content)));
}

function stockForm(ctx, type, products) {
  const titles = { STOCK_IN: 'Goods received', STOCK_OUT: 'Goods dispatched', ADJUSTMENT: 'Stock adjustment' };
  const prod = select('productId', products.map((p) => [p.id, `${p.name} (${p.quantityInStock} ${p.unit || ''})`]));
  formDialog({
    title: titles[type],
    fields: [
      field('Product', prod),
      field(type === 'ADJUSTMENT' ? 'Quantity change (use - to reduce)' : 'Quantity', input('quantity', { required: true, type: 'number', step: '1', min: type === 'ADJUSTMENT' ? undefined : '1', inputmode: 'numeric' })),
      field('Reference (PO / delivery order)', input('referenceNo', { maxlength: 100 })),
      field(type === 'ADJUSTMENT' ? 'Reason (required)' : 'Note', input('notes', { required: type === 'ADJUSTMENT', maxlength: 255 }),
        type === 'ADJUSTMENT' ? 'e.g. damaged, miscounted, returned' : null),
    ],
    onSubmit: async (val) => {
      await post('/api/transactions', {
        productId: Number(val.productId), transactionType: type, quantity: Number(val.quantity),
        notes: val.notes.trim() || null, referenceNo: val.referenceNo.trim() || null,
      });
      toast('Recorded');
      ctx.reload();
    },
  });
}

// ----------------------------------------------------- suppliers & categories
function simpleCrud({ title, sub, path, columns, formFields, toBody }) {
  return async (ctx) => {
    const page = await get(path + BIG);
    const isAdmin = ctx.user.role === 'ADMIN';
    const open = (row) => {
      const fields = formFields(row || {});
      formDialog({
        title: (row ? 'Edit ' : 'New ') + title.toLowerCase().replace(/s$/, ''),
        fields: fields.map(([label, name, opts]) => field(label, input(name, { value: row ? row[name] : '', ...opts }))),
        onSubmit: async (val) => {
          const body = toBody(val);
          await (row ? put(`${path}/${row.id}`, body) : post(path, body));
          toast('Saved');
          ctx.reload();
        },
      });
    };
    const remove = async (row) => {
      if (!(await confirmDialog('Delete?', `Delete "${row.name}"? This cannot be undone.`, 'Delete'))) return;
      try {
        await api('DELETE', `${path}/${row.id}`);
        toast('Deleted');
        ctx.reload();
      } catch (e) { toast(describe(e), true); }
    };
    return h('div', {},
      header(title, sub, isAdmin ? h('button', { class: 'primary', text: 'Add', onclick: () => open(null) }) : null),
      h('div', { class: 'card flush' }, table([
        ...columns,
        isAdmin ? { label: '', actions: true, render: (r) => [
          h('button', { class: 'small', text: 'Edit', onclick: () => open(r) }),
          h('button', { class: 'small danger', text: 'Delete', onclick: () => remove(r) }),
        ] } : null,
      ].filter(Boolean), page.content, 'Nothing here yet.')));
  };
}

export const suppliers = simpleCrud({
  title: 'Suppliers', sub: 'Who you buy from.', path: '/api/suppliers',
  columns: [
    { label: 'Supplier', render: (s) => h('strong', { text: s.name }) },
    { label: 'Contact', render: (s) => [s.contactPerson, s.phone].filter(Boolean).join(' · ') || '-' },
    { label: 'Email', render: (s) => s.email || '-' },
  ],
  formFields: () => [['Name', 'name', { required: true, maxlength: 150 }], ['Contact person', 'contactPerson', { maxlength: 150 }],
    ['Phone', 'phone', { maxlength: 50, type: 'tel' }], ['Email', 'email', { type: 'email', maxlength: 150 }], ['Address', 'address', { maxlength: 255 }]],
  toBody: (v) => ({ name: v.name.trim(), contactPerson: v.contactPerson.trim() || null, phone: v.phone.trim() || null, email: v.email.trim() || null, address: v.address.trim() || null }),
});

export const categories = simpleCrud({
  title: 'Categories', sub: 'Jam, flour, sugar, margarine, milk powder...', path: '/api/categories',
  columns: [{ label: 'Category', render: (c) => h('strong', { text: c.name }) }, { label: 'Description', render: (c) => c.description || '-' }],
  formFields: () => [['Name', 'name', { required: true, maxlength: 100 }], ['Description', 'description', { maxlength: 255 }]],
  toBody: (v) => ({ name: v.name.trim(), description: v.description.trim() || null }),
});
