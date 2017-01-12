#r "../node_modules/fable-core/Fable.Core.dll"
#load "Main.fsx"

open Fable.Core
open Fable.Import
open Fable.Import.Browser

open Fable.Core.JsInterop


let contentNodeClass = "app"

#if DEV_HMR

type IModule =
  abstract hot: obj with get, set

let [<Global>] [<Emit("module")>] Module : IModule = failwith "JS only"

let node = document.querySelector ("." + contentNodeClass)

[<Emit("module.hot.accept();")>]
let accept() = jsNative

if not <| isNull Module.hot then
  accept()

  Module.hot?dispose(fun _ ->
    node.removeChild(node.firstChild) |> ignore
  ) |> ignore
#endif

App.Main.start contentNodeClass
