import { useLab } from '../lab-provider';
import { PageHeading } from '../shared';

export function ProductsView() {
  const { page, l } = useLab();
  return <>
    <PageHeading eyebrow={l('Products', 'Sản phẩm')}
      title={l('Research products', 'Sản phẩm nghiên cứu')} description="" />
    {!page?.sections.some(section => section.visible !== false) && <section className="section">
      <div className="container"><p role="status">{l('Research products are being updated.', 'Các sản phẩm nghiên cứu đang được cập nhật.')}</p></div>
    </section>}
  </>;
}
