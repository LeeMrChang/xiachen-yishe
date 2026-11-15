#!/usr/bin/env python3
"""Exercise collection writes only against the CI smoke server and its disposable DB."""
import json
from urllib.request import Request, urlopen

BASE = 'http://127.0.0.1:18099'
token = ''


def api(method, path, body=None, success=True):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = Request(BASE + '/api/admin' + path, method=method, headers=headers,
                      data=None if body is None else json.dumps(body).encode())
    with urlopen(request, timeout=10) as response:
        result = json.load(response)
    assert (result['code'] == 0) == success, f'{method} {path}: {result}'
    return result.get('data')


token = api('POST', '/auth/login', {'username': 'admin', 'password': 'ci-admin-only'})['token']
collection = api('POST', '/collections', {'name': 'CI collection', 'story': 'initial', 'sortNo': 1})
cid = collection['id']
assert collection['status'] == 'DRAFT'
api('POST', f'/collections/{cid}/publish', success=False)
api('POST', '/collections', {'name': 'CI collection'}, success=False)
api('POST', '/collections', {'name': ''}, success=False)

products = []
for i in range(2):
    product = api('POST', '/products', {'title': f'CI product {i}', 'sortWeight': i})
    pid = product['id']
    api('PUT', f'/products/{pid}/skus', {'items': [{'color': 'black', 'size': 'M', 'priceCent': 10000}]})
    api('POST', f'/products/{pid}/images', {'url': 'https://example.invalid/ci.jpg', 'width': 100, 'height': 100})
    api('POST', f'/products/{pid}/publish')
    products.append(pid)

saved = api('PUT', f'/collections/{cid}/items', {'productIds': products[::-1] + [products[0]]})
assert [p['productId'] for p in saved['products']] == products[::-1], 'Ordering/deduplication failed'
api('PUT', f'/collections/{cid}/items', {'productIds': [9223372036854775807]}, success=False)
assert len(api('GET', f'/collections/{cid}')['products']) == 2, 'Failed save lost existing members'
assert api('POST', f'/collections/{cid}/publish')['status'] == 'ON'
updated = api('PUT', f'/collections/{cid}', {'name': 'CI renamed', 'story': 'updated', 'sortNo': 2, 'status': 'ON'})
assert updated['name'] == 'CI renamed' and updated['story'] == 'updated'
rows = api('GET', '/collections?keyword=CI%20renamed&status=ON')
assert len(rows) == 1 and rows[0]['productCount'] == 2

theme = api('POST', '/home-themes', {'name': 'CI theme', 'status': 'DRAFT'})
tid = theme['id']
api('PUT', f'/home-themes/{tid}/modules', {'modules': [{'type': 'COLLECTION', 'refId': cid, 'enabled': True}]})
api('DELETE', f'/collections/{cid}', success=False)
api('PUT', f'/home-themes/{tid}/modules', {'modules': []})
assert api('POST', f'/collections/{cid}/offline')['status'] == 'OFF'
api('DELETE', f'/collections/{cid}')
api('GET', f'/collections/{cid}', success=False)
print('Collection smoke passed: create, validation, membership, order, publish, update, references and delete')
