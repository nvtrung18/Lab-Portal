import { Search, X } from 'lucide-react';
export function SearchInput({ value, onChange, placeholder }: {
  value: string; onChange: (value: string) => void; placeholder: string;
}) {
  return <div className="search-field">
    <Search size={18} aria-hidden="true" />
    <input type="search" aria-label={placeholder} placeholder={placeholder} value={value} onChange={event => onChange(event.target.value)} />
    {value && <button type="button" onClick={() => onChange('')} aria-label={placeholder.startsWith('Search') ? 'Clear search' : 'Xóa tìm kiếm'}><X size={15} /></button>}
  </div>;
}
