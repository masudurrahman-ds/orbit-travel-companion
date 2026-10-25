'use strict';
/** Group trip expense splitting. Amounts are whole taka. This only calculates who owes whom; it does not move money. */

/** Splits `amount` equally among `ids`. Any leftover taka goes one each to the first members, so the parts always add up. */
function equalShares(amount, ids) {
  const base = Math.floor(amount / ids.length), extra = amount - base * ids.length;
  return ids.map((id, i) => ({ id, share: base + (i < extra ? 1 : 0) }));
}

/** net[id] > 0 means the group owes that member money; < 0 means the member owes the group. */
function balances(memberIds, expenses) {
  const net = Object.fromEntries(memberIds.map((id) => [id, 0]));
  for (const e of expenses) {
    net[e.paidBy] = (net[e.paidBy] || 0) + e.amount;
    for (const s of e.shares) net[s.id] = (net[s.id] || 0) - s.share;
  }
  return net;
}

/** Greedy matching of the biggest debtor with the biggest creditor. Produces at most (members - 1) transfers. */
function settlements(net) {
  const creditors = [], debtors = [];
  for (const [id, v] of Object.entries(net)) { if (v > 0) creditors.push({ id, v }); else if (v < 0) debtors.push({ id, v: -v }); }
  const byAmount = (a, b) => b.v - a.v || a.id.localeCompare(b.id);
  creditors.sort(byAmount); debtors.sort(byAmount);
  const out = [];
  let i = 0, j = 0;
  while (i < creditors.length && j < debtors.length) {
    const pay = Math.min(creditors[i].v, debtors[j].v);
    out.push({ from: debtors[j].id, to: creditors[i].id, amount: pay });
    creditors[i].v -= pay; debtors[j].v -= pay;
    if (creditors[i].v === 0) i++;
    if (debtors[j].v === 0) j++;
  }
  return out;
}

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no 0/O/1/I
module.exports = { equalShares, balances, settlements, CODE_ALPHABET };
