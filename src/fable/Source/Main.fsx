#r "../node_modules/fable-core/Fable.Core.dll"
#r "../node_modules/fable-react/Fable.React.dll"
#r "../node_modules/fable-elmish/Fable.Elmish.dll"
#r "../node_modules/fable-elmish-react/Fable.Elmish.React.dll"
#load "Model.fsx"

namespace App

open System
open Fable.Core
open Fable.Core.JsInterop
module Browser = Fable.Import.Browser

open Model

module Main =

  type Model =
    { Input: string }

    static member initial =
      #if DEV_HMR
      // This section is used to maintain state between HMR
      if not <| isNull (unbox Browser.window?storage) then
        unbox Browser.window?storage
      else
        let model = { Input = "" }
        Browser.window?storage <- model
        model
      #else
      { Input = "" }
      #endif

  let init _ = Model.initial, []

  // Actions supported by the application
  type Msg =
    | ChangeInput of string

  let update (msg:Msg) (model: Model) =
    let model', msg' =
      match msg with
      | ChangeInput s ->
        { model with Input = s } , []

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
      [
        label
          []
          [unbox "Enter name: "]
        input
          [ OnInput (fun e -> ChangeInput (unbox e?target?value) |> dispatch ) ]
          []
        br [] []
        span
          []
          [unbox (sprintf "Helloiasueran %s" model.Input)]
      ]

  open Elmish
  open Elmish.React

  let start contentNodeClass = Elmish.Program.mkProgram init update view
  #if DEV_HMR
                               |> Program.withConsoleTrace
  #endif
                               |> Program.withReact contentNodeClass
                               |> Program.run