"""Recria app/google-services.json a partir de uma versão já publicada do Cockpit.

A configuração do Firebase de uma app Android não é segredo: vai dentro de cada APK. Quando o segredo
GOOGLE_SERVICES_JSON não está definido, o CI lê esses valores do último APK publicado e volta a escrever o
ficheiro, para a versão nova continuar com login Google e cópia na nuvem.

python ci/gsj_from_apk.py <apk> app/google-services.json
"""
import json
import sys

from androguard.core.apk import APK
from loguru import logger

logger.remove()

apk, out = sys.argv[1], sys.argv[2]
a = APK(apk)
r = a.get_android_resources()
pkg = a.get_package()


def s(name):
    try:
        c = r.get_resolved_res_configs(r.get_res_id_by_key(pkg, "string", name))
        return c[0][1] if c else ""
    except Exception:
        return ""


app_id, key, sender, project = s("google_app_id"), s("google_api_key"), s("gcm_defaultSenderId"), s("project_id")
if not (app_id and key and project):
    sys.exit("O APK não tem configuração do Firebase.")
web = s("default_web_client_id")
oauth = [{"client_id": web, "client_type": 3}] if web else []
gsj = {
    "project_info": {"project_number": sender, "firebase_url": s("firebase_database_url"), "project_id": project, "storage_bucket": s("google_storage_bucket")},
    "client": [{
        "client_info": {"mobilesdk_app_id": app_id, "android_client_info": {"package_name": pkg}},
        "oauth_client": oauth,
        "api_key": [{"current_key": key}],
        "services": {"appinvite_service": {"other_platform_oauth_client": oauth}},
    }],
    "configuration_version": "1",
}
with open(out, "w") as f:
    json.dump(gsj, f, indent=2)
print("google-services.json recriado para", project, pkg)
