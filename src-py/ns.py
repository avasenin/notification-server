#!/usr/bin/python

import requests
import json

SERVER = "http://localhost:8080/api/notify"
VALID_KEYS = ["signature", "tags", "short-message", "message"]
LIST_KEYS = ["tags", "signature"]

def validate_payload(details):
    kdiff = set.symmetric_difference(
            set(details.keys()),
            set(VALID_KEYS))
    if len(kdiff):
        raise Exception("Missing or excess keys", kdiff)

    not_lists = [k for k in LIST_KEYS if (type(details[k]) != list)]
    if not_lists:
        raise Exception("Following fields must be lists", not_lists)
    
def notify_raw(details, server):
    validate_payload(details)

    return requests.post(url=server, data=json.dumps(details),
            headers={"content-type": "application/json"})

def notify(server=SERVER,
        signature=["project:ds", "state:up"],
        tags=["source:pingdom", "test:internal"],
        short_message="Just a test message //avasenin"):
    """ Use notify for your everyday needs.
    Signature and tags should be lists of strings, short_message - plain string.
    Exceptions will be thrown on invalid inputs. """

    details = {
            "signature": signature,
            "tags": tags,
            "message": short_message,
            "short-message": short_message}
    return notify_raw(details, server)

if __name__ == "__main__":
    print notify()
