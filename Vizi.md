# Vizi - Tai lieu dac ta du an

## 1. Tong quan

Vizi la nen tang web-to-print cho phep nguoi dung tao, tuy bien va dat in card visit/danh thiep truc tiep tren trinh duyet. Muc tieu cua du an la giup ca nhan, shop nho va doanh nghiep nho co the nhanh chong tao card visit chuyen nghiep tu mau co san hoac tu trang trong, sau do dat in voi cau hinh giay va gia cong phu hop.

San pham khong chi tao ra anh xem truoc. Gia tri cot loi cua Vizi la luu thiet ke duoi dang du lieu co cau truc, cho phep sua lai tung layer, thay text, doi mau, thay logo, can chinh bo cuc, hoan tac/lam lai va xuat file san sang in.

## 2. Muc tieu san pham

### 2.1. Muc tieu chinh

- Cho phep nguoi dung tao card visit tu template co san.
- Cho phep tuy bien noi dung card: ten thuong hieu, logo, ho ten, chuc vu, so dien thoai, email, dia chi, website, mang xa hoi va ma QR.
- Cho phep chinh sua truc quan bang canvas editor.
- Luu thiet ke de nguoi dung co the quay lai sua sau.
- Kiem tra loi co ban truoc khi dat in.
- Cho phep chon cau hinh in: kich thuoc, loai giay, so luong, bo goc, can mang.
- Tao don hang in an tu thiet ke da chot.

### 2.2. Muc tieu mo rong

- Tro ly AI giup sua thiet ke bang ngon ngu tu nhien.
- AI de xuat bo cuc, mau sac, font chu va thanh phan thiet ke.
- Tim kiem icon/hinh khoi bang mo ta tu nhien.
- Xoa nen anh logo hoac anh dai dien.
- Kiem tra chat luong file in nang cao: DPI, bleed, safe zone, font, mau sac.
- Quan ly nhieu mau san pham khac ngoai card visit, vi du postcard, voucher, menu nho, the tich diem.

## 3. Doi tuong nguoi dung

### 3.1. Ca nhan

Nguoi dung can card visit ca nhan de di phong van, networking, lam freelancer, kinh doanh nho hoac gioi thieu dich vu rieng.

### 3.2. Shop nho

Cua hang ca phe, tiem toc, spa, shop thoi trang, shop online va cac cua hang dia phuong can card visit, the tich diem, postcard cam on hoac voucher tang khach.

### 3.3. Don vi in an

Xuon in co the dung Vizi nhu mot cong cu nhan file thiet ke tu khach, giam viec trao doi file loi, sai kich thuoc hoac anh do phan giai thap.

## 4. Pham vi MVP

MVP nen tap trung vao mot luong hoan chinh, nho nhung dung duoc:

1. Nguoi dung xem danh sach template card visit.
2. Nguoi dung chon mot template.
3. He thong mo template trong editor.
4. Nguoi dung sua text, mau sac, logo va ma QR.
5. Nguoi dung luu thiet ke.
6. He thong kiem tra loi co ban truoc khi dat in.
7. Nguoi dung chon cau hinh in.
8. Nguoi dung tao don hang.

Trong MVP, nen uu tien editor thu cong truoc. AI nen duoc tach thanh phase sau de tranh lam he thong phuc tap qua som.

## 5. Tinh nang chinh

### 5.1. Template card visit

Template la mau thiet ke co san, duoc luu duoi dang canvas JSON. Moi template can co thong tin:

- Ten template.
- Danh muc nganh nghe.
- Kich thuoc thiet ke.
- Anh preview.
- Du lieu canvas JSON.
- Trang thai hien thi.

Danh muc goi y:

- Ca nhan.
- Shop thoi trang.
- Quan ca phe.
- Spa/lam dep.
- Bat dong san.
- Cong nghe.
- Freelancer.
- The tich diem.
- Voucher nho.

### 5.2. Canvas editor

Canvas editor la phan quan trong nhat cua san pham. Editor can ho tro:

- Them/sua/xoa text.
- Doi font, co chu, mau chu.
- Upload logo/anh.
- Di chuyen, thu phong, xoay layer.
- Can trai, can giua, can phai.
- Dua layer len/xuong.
- Khoa layer.
- An/hien layer.
- Undo/redo.
- Zoom in/zoom out.
- Xem mat truoc va mat sau cua card.
- Hien safe zone va bleed zone.

Thu vien frontend co the can nhac:

- Fabric.js neu uu tien 2D canvas editor de thao tac layer.
- Konva.js neu muon lam editor theo scene graph gon hon.
- React Flow khong phu hop lam canvas thiet ke in an.

### 5.3. Quan ly layer

Moi object tren canvas nen duoc coi la mot layer co id rieng. Layer can co metadata de phuc vu editor, AI va undo/redo:

- `id`
- `type`: text, image, shape, qr, group
- `name`
- `locked`
- `visible`
- `x`
- `y`
- `width`
- `height`
- `rotation`
- `zIndex`
- `editable`
- `semanticRole`: logo, brandName, phone, email, address, qrCode, background

Khong nen chi luu anh PNG/JPG vi se mat kha nang sua tung thanh phan.

### 5.4. Luu thiet ke

Thiet ke cua nguoi dung nen duoc luu thanh project/design rieng, tach voi template goc.

Thong tin toi thieu:

- Ten thiet ke.
- User so huu.
- Template goc neu co.
- Canvas JSON hien tai.
- Kich thuoc.
- Mat truoc/mat sau.
- Thoi gian tao.
- Thoi gian cap nhat.

Neu nguoi dung sua template, he thong phai clone template thanh design cua nguoi dung, khong sua truc tiep template goc.

### 5.5. Undo/redo va lich su phien ban

Undo/redo co the lam o frontend bang stack state trong phien lam viec. Backend nen luu cac snapshot quan trong de khoi phuc:

- Snapshot khi mo design.
- Snapshot khi nguoi dung bam Save.
- Snapshot truoc khi dat hang.
- Snapshot sau khi chot don.

Khong nen luu moi thao tac keo tha vao database vi se tang du lieu rat nhanh.

### 5.6. QR code

Nguoi dung co the tao QR code dan den:

- Website.
- Facebook fanpage.
- TikTok.
- Instagram.
- Zalo.
- Linktree/portfolio.
- Google Maps.
- So dien thoai.
- Email.

QR code nen duoc luu duoi dang data co the tao lai, khong chi luu anh raster.

### 5.7. Upload anh

Nguoi dung co the upload logo hoac anh dai dien. He thong can kiem tra:

- Dung luong toi da.
- Dinh dang hop le: PNG, JPG, JPEG, WEBP, SVG neu co chinh sach xu ly rieng.
- Do phan giai toi thieu.
- Ty le kich thuoc.
- Anh co nen trong suot hay khong.

Neu cho phep SVG, can sanitize de tranh XSS. Neu chua co xu ly an toan, nen tam thoi chi cho upload PNG/JPG/WEBP.

### 5.8. Pre-flight check

Truoc khi dat in, he thong can kiem tra:

- Thiet ke dung kich thuoc.
- Text khong nam ngoai safe zone.
- Anh khong qua mo hoac do phan giai qua thap.
- Co noi dung bat buoc nhu ten, so dien thoai hoac ma QR neu template yeu cau.
- Mat truoc/mat sau khong trong rong.
- Bleed zone duoc cau hinh dung.

Muc canh bao nen chia thanh:

- Error: khong cho dat in.
- Warning: cho dat in nhung yeu cau nguoi dung xac nhan.
- Info: goi y cai thien.

### 5.9. Checkout va don hang

Nguoi dung chon cau hinh in:

- Kich thuoc card.
- Loai giay.
- Dinh luong giay.
- So luong.
- Bo goc.
- Can mang mo/bong.
- In mot mat/hai mat.
- Ghi chu don hang.

Don hang can luu snapshot cua design tai thoi diem dat hang. Khong nen chi tham chieu design dang song, vi nguoi dung co the sua design sau khi dat hang.

## 6. Tinh nang AI

AI nen la tinh nang ho tro, khong phai phan bat buoc cua MVP dau tien.

### 6.1. Nguyen tac thiet ke AI

- AI khong duoc ghi de truc tiep toan bo canvas neu khong co xac nhan.
- AI nen tra ve danh sach hanh dong ro rang, vi du doi mau layer, dich layer, sua text.
- Moi hanh dong AI phai co preview truoc khi apply.
- Nguoi dung co the undo moi thay doi do AI tao ra.
- Neu lenh mo ho, he thong phai hoi lai thay vi tu sua sai.

### 6.2. JSON patch/action

Thay vi gui toan bo canvas len AI va nhan lai toan bo canvas moi, nen dung action nho:

```json
{
  "actions": [
    {
      "type": "update_text",
      "layerId": "phone_text",
      "value": "0901 234 567"
    },
    {
      "type": "set_fill",
      "layerId": "brand_name",
      "value": "#1F2937"
    }
  ]
}
```

Loi ich:

- Giam token.
- Giam rui ro AI pha hong canvas.
- De validate.
- De undo.
- De log va debug.

### 6.3. Context gui cho AI

Chi nen gui phan context can thiet:

- Prompt cua nguoi dung.
- Layer dang chon.
- Danh sach layer tom tat.
- Metadata semantic cua layer.
- Anh preview nho neu can phan tich thi giac.

Khong nen gui toan bo lich su edit dai hoac canvas JSON qua lon neu khong can.

### 6.4. Cac tac vu AI nen ho tro

Phase dau:

- Doi tone mau theo nganh nghe.
- Viet lai slogan ngan gon.
- Goi y bo cuc.
- Can chinh layer theo nhom.
- Tao QR label.
- Goi y noi dung card visit.

Phase sau:

- Xoa nen anh.
- Nhan dien layer tu anh/template upload.
- Tim icon bang ngon ngu tu nhien.
- Tao hinh nen/pattern don gian.
- Kiem tra thiet ke co de doc hay khong.

## 7. Kien truc he thong

### 7.1. Kien truc tong quan

Goi y chia thanh 3 phan:

- Frontend: giao dien nguoi dung va canvas editor.
- Backend: API, business logic, auth, database, order.
- Storage: luu anh upload, preview, file xuat in.

Luon giu backend la noi kiem tra quyen va tinh hop le cua du lieu. Frontend chi la client, khong duoc tin tuong tuyet doi.

### 7.2. Backend

Backend hien tai nen dung Spring Boot. Cac module goi y:

```text
com.example.vizi
|-- auth
|-- user
|-- template
|-- design
|-- asset
|-- order
|-- checkout
|-- upload
|-- ai
|-- common
```

Khi du an lon hon, co the tach theo domain ro hon, nhung ban dau khong nen over-engineering.

### 7.3. Frontend

Neu lam frontend rieng, goi y:

- React hoac Vue cho UI.
- Fabric.js hoac Konva.js cho canvas.
- TanStack Query hoac axios/fetch wrapper cho API.
- State editor rieng cho canvas, khong tron qua sau vao global state.

Neu lam nhanh trong Java mon hoc, co the bat dau bang Thymeleaf + JavaScript, nhung canvas editor phuc tap se de lam hon voi frontend framework.

### 7.4. Database

Nen dung database quan he cho MVP:

- PostgreSQL neu muon linh hoat va manh ve JSON.
- MySQL neu moi truong hoc tap/hosting dang quen.
- SQL Server neu project yeu cau theo stack san co.

Canvas JSON co the luu bang `jsonb` trong PostgreSQL hoac `text/longtext` trong MySQL. Neu dung SQL Server co the dung `nvarchar(max)`.

## 8. Database schema goi y

### 8.1. `users`

Luu tai khoan nguoi dung.

| Column | Type | Ghi chu |
| --- | --- | --- |
| id | bigint | Primary key |
| email | varchar | Unique |
| password_hash | varchar | Neu dung dang nhap noi bo |
| full_name | varchar | Ten hien thi |
| role | varchar | USER, ADMIN |
| created_at | timestamp | Thoi gian tao |
| updated_at | timestamp | Thoi gian cap nhat |

### 8.2. `templates`

Luu template goc.

| Column | Type | Ghi chu |
| --- | --- | --- |
| id | bigint | Primary key |
| name | varchar | Ten template |
| category | varchar | Danh muc |
| preview_url | varchar | Anh xem truoc |
| width_mm | decimal | Chieu rong thuc te |
| height_mm | decimal | Chieu cao thuc te |
| canvas_json | text/json | Du lieu thiet ke |
| active | boolean | Co hien thi khong |
| created_at | timestamp | Thoi gian tao |

### 8.3. `designs`

Luu thiet ke cua nguoi dung.

| Column | Type | Ghi chu |
| --- | --- | --- |
| id | bigint | Primary key |
| user_id | bigint | Chu so huu |
| template_id | bigint | Template goc, nullable |
| name | varchar | Ten design |
| canvas_json | text/json | Trang thai hien tai |
| front_preview_url | varchar | Preview mat truoc |
| back_preview_url | varchar | Preview mat sau |
| width_mm | decimal | Chieu rong |
| height_mm | decimal | Chieu cao |
| created_at | timestamp | Thoi gian tao |
| updated_at | timestamp | Thoi gian cap nhat |

### 8.4. `design_snapshots`

Luu snapshot quan trong cua design.

| Column | Type | Ghi chu |
| --- | --- | --- |
| id | bigint | Primary key |
| design_id | bigint | Design lien quan |
| user_id | bigint | Chu so huu |
| reason | varchar | save, checkout, restore |
| canvas_json | text/json | Du lieu snapshot |
| created_at | timestamp | Thoi gian tao |

### 8.5. `assets`

Luu tai nguyen upload hoac asset he thong.

| Column | Type | Ghi chu |
| --- | --- | --- |
| id | bigint | Primary key |
| user_id | bigint | Nullable neu asset he thong |
| type | varchar | image, icon, shape |
| file_url | varchar | Duong dan file |
| file_name | varchar | Ten file |
| mime_type | varchar | Dinh dang |
| size_bytes | bigint | Dung luong |
| width_px | int | Chieu rong pixel |
| height_px | int | Chieu cao pixel |
| created_at | timestamp | Thoi gian tao |

### 8.6. `orders`

Luu don hang tong.

| Column | Type | Ghi chu |
| --- | --- | --- |
| id | bigint | Primary key |
| user_id | bigint | Chu don hang |
| status | varchar | DRAFT, PENDING_PAYMENT, PAID, PRINTING, DONE, CANCELLED |
| total_amount | decimal | Tong tien |
| customer_note | text | Ghi chu |
| created_at | timestamp | Thoi gian tao |
| updated_at | timestamp | Thoi gian cap nhat |

### 8.7. `order_items`

Luu tung san pham trong don.

| Column | Type | Ghi chu |
| --- | --- | --- |
| id | bigint | Primary key |
| order_id | bigint | Don hang |
| design_id | bigint | Design goc |
| design_snapshot_json | text/json | Snapshot tai luc dat hang |
| print_config_json | text/json | Cau hinh in |
| quantity | int | So luong |
| unit_price | decimal | Don gia |
| subtotal | decimal | Thanh tien |

## 9. API goi y

### 9.1. Template

```http
GET /api/templates
GET /api/templates/{id}
POST /api/admin/templates
PUT /api/admin/templates/{id}
DELETE /api/admin/templates/{id}
```

### 9.2. Design

```http
GET /api/designs
GET /api/designs/{id}
POST /api/designs/from-template/{templateId}
POST /api/designs
PUT /api/designs/{id}
DELETE /api/designs/{id}
POST /api/designs/{id}/snapshots
GET /api/designs/{id}/snapshots
POST /api/designs/{id}/restore/{snapshotId}
```

Tat ca API design phai filter theo current user, khong chi query theo `id`.

### 9.3. Upload

```http
POST /api/uploads/images
GET /api/assets
DELETE /api/assets/{id}
```

### 9.4. Pre-flight

```http
POST /api/designs/{id}/preflight
```

Response goi y:

```json
{
  "valid": false,
  "issues": [
    {
      "level": "ERROR",
      "code": "LOW_IMAGE_RESOLUTION",
      "message": "Logo co do phan giai qua thap de in ro net.",
      "layerId": "logo"
    }
  ]
}
```

### 9.5. Order

```http
POST /api/orders
GET /api/orders
GET /api/orders/{id}
POST /api/orders/{id}/cancel
```

### 9.6. AI

```http
POST /api/ai/design-actions
```

Payload goi y:

```json
{
  "designId": 1,
  "prompt": "Doi mau card sang phong cach sang trong hon",
  "activeLayerId": "brand_name",
  "canvasSummary": [
    {
      "id": "brand_name",
      "type": "text",
      "text": "Vizi",
      "semanticRole": "brandName"
    }
  ]
}
```

## 10. Luong nghiep vu chinh

### 10.1. Tao design tu template

1. Nguoi dung mo danh sach template.
2. Nguoi dung chon template.
3. Backend clone `canvas_json` cua template thanh row moi trong `designs`.
4. Frontend mo design moi trong editor.
5. Nguoi dung sua noi dung.
6. Nguoi dung bam Save.
7. Backend validate owner va cap nhat canvas JSON.

### 10.2. Dat in

1. Nguoi dung bam Dat in.
2. Frontend goi API pre-flight.
3. Neu co error, chan checkout.
4. Neu chi co warning, hien canh bao va cho xac nhan.
5. Nguoi dung chon cau hinh in.
6. Backend tao order va order item.
7. Backend luu snapshot design vao order item.
8. He thong chuyen sang thanh toan hoac trang xac nhan.

### 10.3. AI sua thiet ke

1. Nguoi dung chon layer hoac nhap lenh tong quat.
2. Frontend gui prompt va canvas summary len backend.
3. Backend goi AI provider.
4. Backend validate danh sach action tra ve.
5. Frontend hien preview thay doi.
6. Nguoi dung chap nhan hoac tu choi.
7. Neu chap nhan, frontend apply action vao canvas va tao undo checkpoint.

## 11. Yeu cau phi chuc nang

### 11.1. Hieu nang

- Editor phai phan hoi muot khi keo tha layer.
- Preview template can duoc cache.
- Khong goi API luu lien tuc moi khi nguoi dung keo layer.
- Nen autosave theo debounce, vi du 3-5 giay sau lan sua cuoi.
- File upload can gioi han dung luong.

### 11.2. Tin cay

- Luu snapshot truoc checkout.
- Co co che restore design tu snapshot.
- Don hang phai giu nguyen file/design tai thoi diem dat.
- Khong de viec sua design sau checkout lam thay doi don da dat.

### 11.3. Bao mat

- Tat ca API user data phai yeu cau dang nhap.
- API lay/sua/xoa design phai kiem tra owner.
- Khong hardcode API key, database password, token hoac secret trong source code.
- Upload file phai validate MIME type, extension va dung luong.
- Neu hien thi HTML tu user input, phai escape/sanitize.
- Neu dung cookie session, can bat CSRF protection cho form/API phu hop.
- Public endpoint nhu login/register/contact can co rate limiting.

## 12. Cong nghe de xuat

### 12.1. Backend

- Java LTS nen uu tien Java 21 neu muon on dinh.
- Spring Boot.
- Spring Web cho REST API.
- Spring Data JPA cho database.
- Bean Validation cho validate request.
- Spring Security khi them dang nhap.
- Flyway hoac Liquibase cho migration.

Luu y: nen kiem tra phien ban moi nhat va trang thai bao mat tren nguon chinh thuc nhu Maven Central truoc khi chot dependency.

### 12.2. Frontend

- React hoac Vue.
- Fabric.js hoac Konva.js cho canvas.
- QR generation library.
- Thu vien upload/crop anh neu can.

Luu y: nen kiem tra phien ban moi nhat tren npm va changelog bao mat truoc khi cai package.

### 12.3. Database va storage

- PostgreSQL cho production neu can JSON tot.
- H2 chi nen dung test/dev nho.
- Object storage hoac local storage co kiem soat cho file upload.

### 12.4. Moi truong cloud va CSDL can chot truoc

Vi Vizi la web app chay cloud, can chot moi truong runtime va database ngay tu dau de tranh thiet ke sai cach deploy, sai kieu ket noi database hoac luu file sai cho production.

#### Phuong an de xuat cho MVP

- Runtime backend: Spring Boot chay nhu mot long-running web service, khong chay serverless function.
- Java runtime: Java 21 LTS cho production. Khong nen dung Java 26 trong giai doan nay vi de vuong cloud build image va moi truong local.
- Build: Maven wrapper.
- Deploy backend: Railway hoac Render deu phu hop voi Spring Boot. Neu can nhanh, Railway de khoi tao va gan PostgreSQL cung project. Neu can on dinh theo kieu web service truyen thong, Render cung la lua chon tot.
- Database production: Managed PostgreSQL.
- Database local: PostgreSQL local bang Docker hoac H2 chi dung cho test nhanh. Neu can hanh vi giong production, uu tien PostgreSQL local.
- File storage: khong luu file upload vao filesystem cua app container. Dung object storage nhu Supabase Storage, S3-compatible storage hoac Cloudflare R2.
- Migration: Flyway hoac Liquibase. Khong tao/sua table thu cong tren production.

#### Lua chon CSDL

Chot PostgreSQL la CSDL chinh cho Vizi.

Ly do:

- Phu hop du lieu quan he: users, templates, designs, orders, order_items.
- Ho tro JSON/JSONB tot cho `canvas_json`, `print_config_json`, `design_snapshot_json`.
- De them index, constraint, transaction va migration ve sau.
- Phu hop voi cloud managed database va Spring Boot/JPA.

Khong nen chon MongoDB cho MVP vi du an van co nhieu quan he ro rang giua user, design, order va payment. Khong nen dung MySQL neu muc tieu la thao tac JSON canvas nhieu, du MySQL van co the dung neu bat buoc theo moi truong hoc tap.

#### Ket noi database tren cloud

Backend Spring Boot la long-running service nen can connection pool o application layer bang HikariCP. Neu database provider co pooler, can chon dung mode:

- Persistent backend: uu tien direct connection hoac session pooler tuy nha cung cap.
- Serverless/edge functions: moi can transaction pooler vi ket noi ngan han nhieu.
- Migration tool: nen dung direct connection, khong dung transaction pooler neu tool khong ho tro.

Can gioi han pool size ngay tu dau. MVP co the bat dau:

```properties
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.minimum-idle=1
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

Gia tri nay can dieu chinh theo goi database that te. Khong tang pool size neu chua xem connection limit cua database provider.

#### Bien moi truong bat buoc

Production khong duoc hardcode cau hinh vao source code. Can dung environment variables:

```text
PORT
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
APP_BASE_URL
STORAGE_ENDPOINT
STORAGE_BUCKET
STORAGE_ACCESS_KEY
STORAGE_SECRET_KEY
AI_PROVIDER_API_KEY
JWT_SECRET
```

Local development co the dung `.env` hoac application profile rieng, nhung file chua secret phai nam trong `.gitignore`.

#### Spring profile de xuat

- `local`: chay tren may dev, co the dung PostgreSQL local.
- `test`: dung H2 hoac Testcontainers PostgreSQL.
- `staging`: cloud gan production, dung database rieng de test deploy.
- `prod`: production, chi lay cau hinh tu environment variables.

Khong dung chung database giua local, staging va production.

#### Cau hinh file upload tren cloud

Filesystem cua container cloud co the bi xoa khi redeploy hoac scale service. Vi vay:

- Anh upload cua nguoi dung phai luu vao object storage.
- Database chi luu metadata va URL/key cua file.
- File preview/export nen co key theo user/design/order.
- Can gioi han dung luong upload va validate MIME type.

Goi y key:

```text
users/{userId}/designs/{designId}/uploads/{assetId}.{ext}
users/{userId}/designs/{designId}/preview/front.png
orders/{orderId}/print-files/{orderItemId}.pdf
```

#### Quy tac schema cho PostgreSQL

- Dung `bigint` cho primary key.
- Dung `timestamptz` cho thoi gian.
- Dung `numeric(10,2)` hoac scale phu hop cho tien.
- Dung `text` thay vi `varchar(n)` neu khong co gioi han nghiep vu ro rang.
- Dung `jsonb` cho `canvas_json`, `print_config_json`, `design_snapshot_json` neu PostgreSQL.
- Them foreign key va index cho cac cot `user_id`, `template_id`, `design_id`, `order_id`.
- Neu he thong multi-user, moi bang du lieu rieng cua user phai co owner field va co chinh sach truy cap ro rang.

#### Quyet dinh mac dinh cho giai doan dau

Trong giai doan dau, Vizi nen chot stack nhu sau:

```text
Backend: Spring Boot + Java 21 LTS
Build: Maven
Runtime: Long-running web service
Cloud app host: Railway hoac Render
Database: Managed PostgreSQL
Local database: PostgreSQL local, H2 chi dung cho test nho
Migration: Flyway
File storage: S3-compatible object storage
Frontend: React + Canvas library hoac Thymeleaf + JavaScript neu can lam don gian truoc
```

Neu can lam nhanh cho demo mon hoc, co the chay fullstack tren mot Spring Boot app truoc. Tuy nhien van phai giu database la PostgreSQL va file upload la object storage/development storage co abstraction de sau nay dua len cloud khong phai viet lai.

### 12.5. Phuong an AI va API

Vizi se dung Gemini API lam AI provider chinh trong giai doan dau de toi uu chi phi. Tuy nhien khong nen dung mot model duy nhat cho moi tac vu. Can tach nhom model theo dung viec:

| Tinh nang | Model/API de xuat | Muc dich |
| --- | --- | --- |
| AI sua text | `gemini-3.1-flash-lite` | Viet lai slogan, ten dich vu, loi cam on, thong tin card |
| AI goi y mau | `gemini-3.1-flash-lite` | Tra ve palette, style guide ngan, mau nen/mau chu |
| AI tra ve JSON patch | `gemini-3.1-flash-lite` | Tra ve danh sach action sua canvas |
| AI tim asset | `gemini-embedding-001` hoac `gemini-embedding-2` | Tao vector de tim icon, shape, asset theo mo ta |
| AI xoa phong | `gemini-3.1-flash-lite-image` | Tao anh moi da xoa nen tu anh khach upload |
| AI tao thiet ke tu y tuong khach | `gemini-3.1-flash-lite` | Tao canvas JSON editable tu prompt va thong tin nganh nghe |
| AI tao/sua anh bo sung | `gemini-3.1-flash-lite-image` | Chi dung khi can tao background, pattern, image asset |

#### Nguyen tac quan trong

- API key Gemini chi duoc dat o Backend Spring Boot.
- Frontend Vue khong duoc goi truc tiep Gemini API.
- Backend phai validate tat ca output AI truoc khi apply vao canvas.
- AI khong duoc ghi de truc tiep thiet ke cua nguoi dung neu chua co preview va xac nhan.
- Khong luu de len anh goc khi xoa phong. Luon luu ca anh goc va anh da xu ly.
- Dung paid tier cho production neu co anh/du lieu khach hang that, vi free tier co the dung du lieu de cai thien san pham theo chinh sach nha cung cap.

#### Luong tong quat

```text
Vue Frontend
  -> gui prompt, layer hien tai, canvas summary, file upload neu co
Spring Boot Backend
  -> kiem tra dang nhap va quota
  -> goi Gemini API
  -> validate JSON/action/image output
  -> luu vao PostgreSQL va Storage
  -> tra ket qua preview ve Frontend
Vue Frontend
  -> hien preview
  -> nguoi dung chap nhan hoac tu choi
```

#### AI sua text

Input toi thieu:

```json
{
  "designId": 1,
  "layerId": "slogan",
  "currentText": "Uy tin chat luong",
  "prompt": "Viet sang trong hon cho spa"
}
```

Output mong muon:

```json
{
  "actions": [
    {
      "type": "update_text",
      "layerId": "slogan",
      "value": "Cham soc tinh te, ve dep ben lau"
    }
  ]
}
```

Backend can kiem tra:

- `designId` thuoc current user.
- `layerId` co ton tai trong canvas.
- Text moi khong vuot gioi han do dai.
- Text khong chua noi dung cam hoac spam.

#### AI goi y mau

AI tra ve palette co cau truc, khong tra ve giai thich dai:

```json
{
  "palette": {
    "primary": "#1F2937",
    "secondary": "#D4AF37",
    "background": "#F8F5EF",
    "text": "#111827"
  },
  "style": "premium_spa",
  "reason": "Phu hop phong cach spa cao cap"
}
```

Frontend chi apply palette sau khi nguoi dung chon "Ap dung".

#### AI JSON patch cho canvas

Day la co che chinh de AI sua thiet ke. AI chi duoc tra ve action hop le:

```json
{
  "actions": [
    {
      "type": "set_fill",
      "layerId": "brandName",
      "value": "#1F2937"
    },
    {
      "type": "move",
      "layerId": "qrCode",
      "x": 420,
      "y": 220
    }
  ]
}
```

Danh sach action nen ho tro ban dau:

- `update_text`
- `set_fill`
- `set_font_size`
- `move`
- `resize`
- `align`
- `toggle_visibility`
- `replace_image`

Khong cho AI tra ve code JavaScript de frontend chay truc tiep.

#### AI tim asset

Khong nen de Gemini Flash-Lite tu "chon dai" asset bang text thuong. Nen dung vector search:

```text
Asset metadata -> embedding -> luu vector vao PostgreSQL/pgvector
Prompt khach -> embedding -> vector search -> tra asset gan nhat
```

Du lieu asset nen co:

```json
{
  "id": 12,
  "name": "gold_sparkle",
  "type": "icon",
  "tags": ["premium", "sparkle", "beauty", "gold"],
  "description": "Bieu tuong lap lanh mau vang phu hop spa va lam dep"
}
```

Neu chua cai `pgvector`, MVP co the tim theo tag/text truoc. Vector search de phase sau.

#### AI xoa phong

Luong xu ly:

```text
1. Khach upload anh/logo.
2. Backend luu anh goc vao object storage.
3. Backend goi `gemini-3.1-flash-lite-image` de xoa nen.
4. Backend luu anh PNG da xoa nen vao object storage.
5. Backend tao asset moi tro ve frontend.
6. Frontend them asset moi vao canvas.
```

Database khong chi luu mot URL. Can luu toi thieu:

```text
original_file_url
processed_file_url
processing_status
processing_type=BACKGROUND_REMOVAL
```

Neu xu ly loi, frontend van phai cho nguoi dung dung anh goc.

#### AI tao thiet ke tu y tuong khach

Khong nen tao card thanh mot anh phang duy nhat. Nen tao canvas JSON co the sua tiep.

Prompt vi du:

```text
Tao card visit cho quan ca phe phong cach vintage, mau nau kem, co QR va so dien thoai.
```

Output nen la canvas editable:

```json
{
  "canvas": {
    "schemaVersion": 1,
    "widthMm": 90,
    "heightMm": 54,
    "background": "#F5EBDD",
    "layers": [
      {
        "id": "brandName",
        "type": "text",
        "text": "Vizi Coffee",
        "x": 40,
        "y": 32,
        "fontSize": 24,
        "fill": "#3B2416"
      },
      {
        "id": "qrCode",
        "type": "qr",
        "value": "https://example.com",
        "x": 430,
        "y": 220,
        "size": 72
      }
    ]
  }
}
```

Neu khach upload anh mau:

```text
Anh mau -> Gemini phan tich style -> tao canvas JSON tuong tu -> chi goi image model neu can tao background/image asset.
```

#### API backend de xuat cho AI

```http
POST /api/ai/text/rewrite
POST /api/ai/design/palette
POST /api/ai/design/patch
POST /api/ai/design/generate
POST /api/ai/assets/search
POST /api/ai/images/remove-background
```

Tat ca endpoint AI phai:

- Yeu cau dang nhap.
- Kiem tra owner cua `designId`, `assetId`.
- Co rate limit theo user/IP.
- Co daily quota theo user.
- Ghi log chi phi uoc tinh, model, token input/output.
- Khong log raw prompt/anh neu co du lieu nhay cam, hoac phai redact/anonymize.

#### Quota va kiem soat chi phi

Goi y quota cho user mien phi:

```text
AI sua text: 30 lan/ngay
AI goi y mau: 20 lan/ngay
AI JSON patch: 20 lan/ngay
AI tim asset: 50 lan/ngay
AI xoa phong: 3 lan/ngay
AI tao thiet ke tu y tuong: 5 lan/ngay
AI tao/sua anh: tat mac dinh hoac 1 lan/ngay
```

Neu co goi tra phi trong tuong lai, co the tang quota theo plan.

#### Bien moi truong cho AI

```text
GEMINI_API_KEY
GEMINI_TEXT_MODEL=gemini-3.1-flash-lite
GEMINI_IMAGE_MODEL=gemini-3.1-flash-lite-image
GEMINI_EMBEDDING_MODEL=gemini-embedding-001
AI_DAILY_TEXT_QUOTA=30
AI_DAILY_IMAGE_QUOTA=3
AI_MAX_INPUT_TOKENS=12000
AI_ENABLE_IMAGE_GENERATION=false
```

Khong commit file `.env` co cac gia tri that len GitHub.

#### Chi phi can du tru

Voi `gemini-3.1-flash-lite`, chi phi text/JSON thuong rat thap neu prompt ngan va chi tra JSON patch. Phan ton tien hon la image:

- Xoa phong/tai tao anh bang `gemini-3.1-flash-lite-image` co chi phi theo anh output.
- Tao anh background hoac card mau nhieu lan se tang chi phi nhanh.
- MVP nen bat text/JSON/asset search truoc, con image generation de sau hoac gioi han quota rat thap.

Phuong an mac dinh cho Vizi:

```text
Bat ngay: sua text, goi y mau, JSON patch, tim asset.
Bat co gioi han: xoa phong.
Tam tat: tao anh hang loat, tao card thanh anh phang.
```

## 13. Lo trinh phat trien

### Phase 0: On dinh nen project

- Sua Java version ve Java 21 LTS hoac mot version cloud/runtime ho tro ro rang.
- Them `.gitignore` chuan.
- Them web starter.
- Tao cau truc package domain.
- Tao health check API don gian.

### Phase 1: Template va design

- CRUD template co ban.
- Tao design tu template.
- Luu/load canvas JSON.
- Preview card.

### Phase 2: Editor

- Editor mat truoc/mat sau.
- Sua text, mau, logo.
- Undo/redo frontend.
- Save/autosave.
- QR code.

### Phase 3: Checkout

- Pre-flight check co ban.
- Chon cau hinh in.
- Tao order.
- Luu snapshot tai checkout.

### Phase 4: Auth va tai khoan

- Dang ky/dang nhap.
- Quan ly design theo user.
- Quan ly order theo user.
- Admin xem order.

### Phase 5: AI

- AI action patch.
- Preview truoc khi apply.
- Goi y mau sac/bo cuc.
- Tim asset bang mo ta.
- Xoa nen anh neu can.

### 13.1. Checklist chia nho de lam den dau kiem thu den do

Nguyen tac thuc hien:

- Moi task chi nen co mot muc tieu ro rang.
- Lam xong task nao thi verify task do ngay.
- Khong lam tiep task phu thuoc neu task truoc chua pass.
- Moi thay doi quan trong nen commit rieng.
- Khong hardcode secret, API key, token, password vao source code.

Definition of Done cho moi task:

- Code chay duoc.
- Test, API check, manual check hoac browser check pass.
- Khong lam vo chuc nang da pass truoc do.
- Khong dua `.env`, log, prompt, secret, memory hoac cau hinh ca nhan vao git.
- Co the clone/mo lai project va chay lai theo huong dan.

#### Nhom 1: Sap xep nen project

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 1 | Kiem tra `git status` va file modified hien co | Biet ro file nao la thay doi cu, file nao la thay doi moi |
| 2 | Tao cau truc `backend/`, `frontend/`, `docs/` | Root repo tach ro BE, FE, docs |
| 3 | Chuyen Spring Boot hien tai vao `backend/` | Mo `backend/` bang IntelliJ duoc |
| 4 | Dat Vue.js trong `frontend/` | Mo `frontend/` bang VS Code duoc |
| 5 | Dua tai lieu vao `docs/` neu can | Tai lieu van doc duoc |
| 6 | Cap nhat `.gitignore` | Tao thu `.env` nhung `git status` khong track |
| 7 | Tao `.env.example` | File chi co key mau, khong co secret that |

#### Nhom 2: Backend Spring Boot chay duoc

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 8 | Chot Java 21 LTS cho backend | `mvnw test` khong con loi Java 26 |
| 9 | Chay test backend lan dau | `BUILD SUCCESS` |
| 10 | Them `spring-boot-starter-web` | App start duoc |
| 11 | Tao `GET /api/health` | Goi API tra ve 200 |
| 12 | Cau hinh `server.port=8080` | Backend chay dung port 8080 |
| 13 | Tao profile `local`, `test`, `prod` | Chay duoc voi profile `local` |
| 14 | Tao exception handler chung | Loi 404/400 tra JSON ro rang |

#### Nhom 3: PostgreSQL va migration

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 15 | Them JPA, PostgreSQL driver, Flyway | `mvnw test` pass |
| 16 | Tao PostgreSQL local bang Docker Compose | Container DB chay duoc |
| 17 | Cau hinh datasource local | Backend connect DB thanh cong |
| 18 | Tao migration Flyway dau tien | Flyway tu chay khi app start |
| 19 | Tao bang `users` | DB co bang `users` |
| 20 | Tao bang `templates` | Insert/select template mau thanh cong |
| 21 | Tao bang `designs` | Co FK den `users` va `templates` |
| 22 | Tao bang `design_snapshots` | Luu snapshot mau thanh cong |
| 23 | Tao bang `assets` | Luu metadata asset mau thanh cong |
| 24 | Tao bang `orders` va `order_items` | Tao order mau co item thanh cong |

#### Nhom 4: Backend API template va design

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 25 | Tao entity `Template` | Repository doc duoc template |
| 26 | Tao `TemplateRepository` | Query active templates pass |
| 27 | Tao `TemplateService` | Unit test service pass |
| 28 | Tao `GET /api/templates` | Tra ve danh sach JSON |
| 29 | Tao `GET /api/templates/{id}` | ID dung tra 200, ID sai tra 404 |
| 30 | Tao entity `Design` | Save/load design pass |
| 31 | Tao `POST /api/designs/from-template/{templateId}` | Clone template thanh design moi |
| 32 | Tao `GET /api/designs/{id}` | Tra ve design dung |
| 33 | Tao `PUT /api/designs/{id}` | Luu `canvas_json` va doc lai giong nhau |
| 34 | Tao snapshot khi save | DB co `design_snapshots` moi |
| 35 | Kiem tra owner design | User khac khong doc/sua duoc design |

#### Nhom 5: Frontend Vue co ban

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 36 | Tao Vue + Vite trong `frontend/` | `npm run dev` chay duoc |
| 37 | Tao `.env.example` cho frontend | Co `VITE_API_BASE_URL` mau |
| 38 | Tao API client | FE goi duoc `/api/health` |
| 39 | Tao layout chinh | UI hien khong loi console |
| 40 | Tao route Home | Vao `/` duoc |
| 41 | Tao route Templates | Render list template tu API |
| 42 | Tao route Editor | Vao `/editor/:designId` khong loi |
| 43 | Cau hinh CORS backend cho FE local | FE `localhost:5173` goi BE `localhost:8080` duoc |

#### Nhom 6: Canvas editor MVP

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 44 | Chot canvas library: Fabric.js hoac Konva.js | Ve duoc object mau |
| 45 | Render khung card 90x54mm | Canvas dung ti le |
| 46 | Render text layer tu JSON | Text hien dung vi tri |
| 47 | Render image layer tu JSON | Anh hien dung URL |
| 48 | Render shape layer | Shape hien dung mau/vi tri |
| 49 | Load design tu backend | Reload trang van hien design |
| 50 | Sua text layer | Text doi tren canvas |
| 51 | Di chuyen layer | State cap nhat `x`, `y` |
| 52 | Doi mau layer | Fill/stroke doi dung |
| 53 | Save canvas JSON | Refresh van con thay doi |
| 54 | Undo frontend | Quay lai state truoc |
| 55 | Redo frontend | Khoi phuc state sau |
| 56 | Hien safe zone va bleed zone | Bat/tat guide duoc |

#### Nhom 7: Upload, asset va QR

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 57 | Preview anh upload tren frontend | Anh hien truoc khi upload |
| 58 | Tao `POST /api/uploads/images` | Backend nhan file va tra URL/assetId |
| 59 | Luu asset metadata | DB co asset record |
| 60 | Them asset vao canvas | Save/load van con anh |
| 61 | Tao QR code frontend | QR scan duoc |
| 62 | Luu QR layer trong `canvas_json` | Reload van co QR |

#### Nhom 8: Pre-flight va checkout

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 63 | Tao pre-flight service | Canvas rong bi bao loi |
| 64 | Check layer ngoai safe zone | Tra warning/error dung |
| 65 | Check anh do phan giai thap | Anh qua nho bi warning |
| 66 | Tao `POST /api/designs/{id}/preflight` | Tra list issues dung |
| 67 | Tao trang checkout | Chon giay, so luong, bo goc |
| 68 | Tao `POST /api/orders` | Tao order va order_items |
| 69 | Luu design snapshot vao order item | Sua design sau checkout khong doi order cu |

#### Nhom 9: AI text, mau va JSON patch

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 70 | Them env Gemini | Thieu key thi bao loi ro |
| 71 | Tao `GeminiClient` backend | Prompt don gian co response |
| 72 | Tao `ai_usage_logs` | Moi request AI co log |
| 73 | Tao quota AI theo user/ngay | Vuot quota tra 429 |
| 74 | Tao `POST /api/ai/text/rewrite` | Tra action `update_text` dung schema |
| 75 | Validate `update_text` | Layer sai/text qua dai bi reject |
| 76 | Tao `POST /api/ai/design/palette` | Tra palette JSON dung hex |
| 77 | Preview va apply palette tren FE | Chua apply thi canvas chua doi |
| 78 | Tao `POST /api/ai/design/patch` | Tra actions trong whitelist |
| 79 | Preview AI patch | Reject thi canvas ve state cu |
| 80 | Snapshot truoc AI patch | DB co `BEFORE_AI_PATCH` |
| 81 | Apply AI patch | Save/load sau apply dung |

#### Nhom 10: AI tao thiet ke tu y tuong

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 82 | Dinh nghia JSON schema canvas AI | JSON sai schema bi reject |
| 83 | Tao `POST /api/ai/design/generate` | Prompt tao duoc canvas editable |
| 84 | Luu AI canvas thanh design moi | Mo editor va sua tiep duoc |
| 85 | Upload anh mau de phan tich style | AI tra style summary |
| 86 | Tao canvas theo style anh mau | Output van editable, khong phai anh phang |

#### Nhom 11: AI tim asset

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 87 | Them tags/description cho asset | Search keyword ra asset dung |
| 88 | Search asset basic theo text/tag | "spa vang" ra asset lien quan |
| 89 | Cai `pgvector` neu can | Tao duoc cot vector |
| 90 | Tao embedding cho asset | Luu vector vao DB |
| 91 | Search asset bang embedding | Mo ta mo ho van ra asset hop ly |

#### Nhom 12: AI xoa phong

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 92 | Tao `POST /api/ai/images/remove-background` | Upload anh tra ve job/result |
| 93 | Luu anh goc | `original_file_url` van ton tai |
| 94 | Goi image model xoa nen | Co processed image |
| 95 | Luu anh da xu ly | FE hien anh da xoa phong |
| 96 | Fallback khi xu ly loi | Loi AI khong lam mat anh goc |

#### Nhom 13: Deploy cloud

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 97 | Tao Dockerfile backend | Build image thanh cong |
| 98 | Cau hinh env prod backend | Khong co secret trong repo |
| 99 | Deploy backend | `/api/health` cloud tra 200 |
| 100 | Build frontend production | `npm run build` pass |
| 101 | Deploy frontend Cloudflare Pages | FE cloud mo duoc |
| 102 | Ket noi DB cloud | Migration chay tren cloud DB |
| 103 | Smoke test production | Template -> design -> save -> AI text -> checkout chay duoc |

#### Nhom 14: Checklist bao mat

| Buoc | Viec lam | Kiem thu |
| --- | --- | --- |
| 104 | Chong SQL Injection | Tat ca query dung parameter/JPA |
| 105 | Chong XSS | Nhap `<script>` khong chay tren FE |
| 106 | CSRF neu dung cookie session | Request ghi du lieu khong hop le bi chan |
| 107 | Chong IDOR | User A khong doc/sua data user B |
| 108 | Rate limit login/upload/AI | Spam request bi 429 |
| 109 | Secret scan truoc commit | Staged files khong co API key/token/password |

## 14. Rui ro ky thuat

### 14.1. Canvas state phuc tap

Neu luu canvas khong co version/schema, ve sau rat kho migrate. Can co truong `schemaVersion` trong canvas JSON.

### 14.2. File in khong dat chat luong

Anh preview dep tren web chua chac in ra dep. Can co pre-flight va quy dinh DPI, bleed, safe zone ro rang.

### 14.3. AI pha hong thiet ke

AI khong nen tu tao canvas moi hoan toan khi nguoi dung chi yeu cau sua nho. Can dung action patch, preview va undo.

### 14.4. IDOR

Neu API chi lay design theo `id` ma khong check `user_id`, nguoi dung co the xem/sua design cua nguoi khac. Moi query du lieu rieng phai filter theo current user.

### 14.5. Upload doc hai

File upload co the chua payload doc hai, SVG co the gay XSS, file qua lon co the lam day storage. Can validate va gioi han ngay tu dau.

## 15. Tieu chi hoan thanh MVP

MVP duoc coi la hoan thanh khi:

- Build va test pass.
- Co the xem danh sach template.
- Co the tao design tu template.
- Co the sua noi dung co ban tren editor.
- Co the luu va mo lai design.
- Co the chay pre-flight check co ban.
- Co the tao order voi cau hinh in.
- API design/order khong bi truy cap cheo user.
- Khong co secret hardcode trong source.

## 16. Security review ngan

- SQL Injection: tat ca truy van phai dung Spring Data JPA, parameterized query hoac named parameter. Khong cong chuoi input nguoi dung vao SQL.
- XSS: escape text nguoi dung khi render. Neu cho upload SVG hoac HTML, phai sanitize hoac chan dinh dang do.
- CSRF: neu dung session cookie, bat CSRF protection cho thao tac ghi du lieu. Neu dung JWT, can cau hinh CORS chat che.
- IDOR: moi API lay/sua/xoa `design`, `asset`, `order` phai filter theo current user ID.
- Brute-force/spam: login, register, contact, upload va AI endpoint can rate limiting theo IP/user/session.
- Secret management: API key AI, database password, JWT secret va token thanh toan phai nam trong environment variables hoac secret manager, khong ghi vao code.
