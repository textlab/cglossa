#r "../node_modules/fable-core/Fable.Core.dll"
#r "../node_modules/fable-react/Fable.React.dll"
#r "../node_modules/fable-elmish/Fable.Elmish.dll"
#r "../node_modules/fable-elmish-react/Fable.Elmish.React.dll"
#load "Model.fsx"
#load "Msg.fsx"

namespace App

open System
open Fable.Core
open Fable.Core.JsInterop
module Browser = Fable.Import.Browser

module Main =

  let initialModel =
    #if DEV_HMR
    // This section is used to maintain state between HMR
    if not <| isNull (unbox Browser.window?storage) then
      unbox Browser.window?storage
    else
      let model = Model.FrontPage Model.FrontPageState.None
      Browser.window?storage <- model
      model
    #else
    Model.FrontPage Model.FrontPageState.None
    #endif

  let init _ = initialModel, []

  // Actions supported by the application
  type Msg =
    | NoOp

  let update (msg:Msg) (model: Model.Model) =
    let model', msg' =
      match msg with
      | NoOp ->
        model, []

    #if DEV_HMR
    // Update the model in storage
    Browser.window?storage <- model'
    #endif

    model', msg'

  /// View

  open Fable.Helpers.React
  open Fable.Helpers.React.Props

  let view model dispatch =
    div
      []
      [ unbox "hei" ]

  open Elmish
  open Elmish.React

  let start contentNodeClass = Elmish.Program.mkProgram init update view
  #if DEV_HMR
                               |> Program.withConsoleTrace
  #endif
                               |> Program.withReact contentNodeClass
                               |> Program.run