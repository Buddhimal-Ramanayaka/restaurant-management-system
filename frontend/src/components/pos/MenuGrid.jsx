import ItemCard from "./ItemCard.jsx";

export default function MenuGrid({ items, onAdd }) {
  if (items.length === 0) {
    return <div className="p-8 text-center text-sm text-slate-500">No items in this category.</div>;
  }
  return (
    <div className="grid grid-cols-2 gap-4 p-4 sm:grid-cols-3 lg:grid-cols-4">
      {items.map((item) => (
        <ItemCard key={item.id} item={item} onAdd={onAdd} />
      ))}
    </div>
  );
}
