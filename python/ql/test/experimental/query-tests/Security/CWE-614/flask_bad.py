from flask import Flask, request, make_response, Response

app = Flask(__name__)


@app.route("/false")
def false():
    resp = make_response()
    resp.set_cookie("name", value="value", secure=False)
    return resp


@app.route("/none")
def none():
    resp = make_response()
    resp.set_cookie("name", value="value", secure=None)
    return resp


@app.route("/flask_Response")
def flask_Response():
    resp = Response()
    resp.headers['Set-Cookie'] = "name=value;"
    return resp


@app.route("/flask_make_response")
def flask_make_response():
    resp = make_response("hello")
    resp.headers['Set-Cookie'] = "name=value;"
    return resp

# if __name__ == "__main__":
#     app.run(debug=True)
